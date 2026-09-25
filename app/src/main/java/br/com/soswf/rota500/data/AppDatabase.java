package br.com.soswf.rota500.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import br.com.soswf.rota500.core.OfferData;
import br.com.soswf.rota500.core.QuoteInput;
import br.com.soswf.rota500.core.QuoteResult;
import br.com.soswf.rota500.core.RobustStats;
import br.com.soswf.rota500.core.RuleDecision;
import br.com.soswf.rota500.core.StrategyAdvice;

public final class AppDatabase extends SQLiteOpenHelper {
    private static final String NAME = "rota500.db";
    private static final int VERSION = 2;
    private static final String[] BACKUP_TABLES = {"work_sessions", "offers", "location_points",
            "gross_snapshots", "private_quotes", "risk_zones", "strategy_events",
            "dynamic_observations", "learning_buckets"};
    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) instance = new AppDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private AppDatabase(Context context) { super(context, NAME, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE work_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_at INTEGER NOT NULL, end_at INTEGER, gross_cents INTEGER NOT NULL DEFAULT 0, start_zone TEXT, end_zone TEXT, learned INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE offers (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, captured_at INTEGER NOT NULL, platform TEXT, fare_cents INTEGER, pickup_km REAL, trip_km REAL, pickup_minutes INTEGER, trip_minutes INTEGER, destination TEXT, source_hash TEXT NOT NULL, confidence REAL, decision TEXT, reason TEXT, unsafe INTEGER NOT NULL DEFAULT 0, UNIQUE(session_id,source_hash))");
        db.execSQL("CREATE TABLE location_points (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, captured_at INTEGER NOT NULL, latitude REAL, longitude REAL, accuracy REAL, speed REAL, provider TEXT)");
        db.execSQL("CREATE TABLE private_quotes (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, client_name TEXT, phone TEXT, scheduled_at TEXT, origin TEXT, destination TEXT, stops TEXT, passengers INTEGER, luggage TEXT, trip_type TEXT, payment TEXT, notes TEXT, input_json TEXT, result_json TEXT, status TEXT NOT NULL DEFAULT 'ORÇAMENTO')");
        db.execSQL("CREATE TABLE risk_zones (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, latitude REAL, longitude REAL, radius_m REAL NOT NULL DEFAULT 600, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE strategy_events (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, created_at INTEGER NOT NULL, source TEXT, action TEXT, title TEXT, explanation TEXT, projected_gross REAL, confidence REAL)");
        db.execSQL("CREATE TABLE dynamic_observations (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, created_at INTEGER NOT NULL, level TEXT, warm_ratio REAL, source TEXT)");
        db.execSQL("CREATE TABLE learning_buckets (bucket_key TEXT PRIMARY KEY, samples INTEGER NOT NULL, expected_gross_hour REAL NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE gross_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, captured_at INTEGER NOT NULL, gross_cents INTEGER NOT NULL)");
        for (String name : new String[]{"Buraco do Boi", "Bacia", "Caixa d'Água", "Forte", "Geneciano"}) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            db.insert("risk_zones", null, values);
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS gross_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, captured_at INTEGER NOT NULL, gross_cents INTEGER NOT NULL)");
    }

    public synchronized long beginSession(String startZone) {
        long active = activeSessionId();
        if (active > 0) return active;
        ContentValues values = new ContentValues();
        values.put("start_at", System.currentTimeMillis());
        values.put("start_zone", startZone);
        long id = getWritableDatabase().insertOrThrow("work_sessions", null, values);
        insertGrossSnapshot(id, 0, System.currentTimeMillis());
        return id;
    }

    public synchronized void endActiveSession(String endZone) {
        DashboardStats stats = dashboardStats();
        if (!stats.hasActiveSession()) return;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("end_at", now);
        values.put("end_zone", endZone);
        getWritableDatabase().update("work_sessions", values, "id=?", new String[]{Long.toString(stats.sessionId)});
        int minutes = (int) Math.max(0, (now - stats.startedAt) / 60_000L);
        if (minutes >= 30) {
            double grossHour = stats.activeGrossCents / 100.0 * 60.0 / minutes;
            updateLearningBucket("GERAL", grossHour);
        }
    }

    public long activeSessionId() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM work_sessions WHERE end_at IS NULL ORDER BY id DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public void setActiveGrossCents(long cents) {
        long id = activeSessionId();
        if (id <= 0) return;
        long now = System.currentTimeMillis();
        long previousAt = 0, previousCents = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT captured_at,gross_cents FROM gross_snapshots WHERE session_id=? ORDER BY captured_at DESC LIMIT 1",
                new String[]{Long.toString(id)})) {
            if (cursor.moveToFirst()) { previousAt = cursor.getLong(0); previousCents = cursor.getLong(1); }
        }
        ContentValues values = new ContentValues();
        values.put("gross_cents", Math.max(0, cents));
        getWritableDatabase().update("work_sessions", values, "id=?", new String[]{Long.toString(id)});
        insertGrossSnapshot(id, Math.max(0, cents), now);
        long minutes = previousAt <= 0 ? 0 : (now - previousAt) / 60_000L;
        long delta = cents - previousCents;
        if (minutes >= 15 && minutes <= 240 && delta >= 0) {
            double rate = delta / 100.0 * 60.0 / minutes;
            updateLearningBucket(currentLearningKey(), rate);
        }
    }

    public boolean insertOffer(OfferData offer, RuleDecision decision, boolean unsafe) {
        ContentValues values = new ContentValues();
        values.put("session_id", activeSessionId());
        values.put("captured_at", System.currentTimeMillis());
        values.put("platform", offer.platform);
        values.put("fare_cents", offer.fareCents);
        values.put("pickup_km", offer.pickupKm);
        values.put("trip_km", offer.tripKm);
        values.put("pickup_minutes", offer.pickupMinutes);
        values.put("trip_minutes", offer.tripMinutes);
        values.put("destination", offer.destination);
        values.put("source_hash", offer.sourceHash);
        values.put("confidence", offer.confidence);
        values.put("decision", decision.outcome.name());
        values.put("reason", decision.reason);
        values.put("unsafe", unsafe ? 1 : 0);
        return getWritableDatabase().insertWithOnConflict("offers", null, values, SQLiteDatabase.CONFLICT_IGNORE) > 0;
    }

    public void insertLocation(Location location) {
        long sessionId = activeSessionId();
        if (sessionId <= 0) return;
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("captured_at", location.getTime() > 0 ? location.getTime() : System.currentTimeMillis());
        values.put("latitude", location.getLatitude());
        values.put("longitude", location.getLongitude());
        values.put("accuracy", location.getAccuracy());
        values.put("speed", location.hasSpeed() ? location.getSpeed() : -1);
        values.put("provider", location.getProvider());
        getWritableDatabase().insert("location_points", null, values);
    }

    public long insertQuote(String client, String phone, String scheduledAt, String origin,
            String destination, String stops, int passengers, String luggage, String tripType,
            String payment, String notes, QuoteInput input, QuoteResult result) {
        ContentValues values = new ContentValues();
        values.put("created_at", System.currentTimeMillis());
        values.put("client_name", client);
        values.put("phone", phone);
        values.put("scheduled_at", scheduledAt);
        values.put("origin", origin);
        values.put("destination", destination);
        values.put("stops", stops);
        values.put("passengers", passengers);
        values.put("luggage", luggage);
        values.put("trip_type", tripType);
        values.put("payment", payment);
        values.put("notes", notes);
        values.put("input_json", quoteInputJson(input).toString());
        values.put("result_json", quoteResultJson(result).toString());
        return getWritableDatabase().insertOrThrow("private_quotes", null, values);
    }

    public List<PrivateRideRecord> recentPrivateRides() {
        List<PrivateRideRecord> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,client_name,phone,scheduled_at,origin,destination,result_json,status FROM private_quotes ORDER BY created_at DESC LIMIT 50", null)) {
            while (cursor.moveToNext()) {
                double price = 0;
                try { price = new JSONObject(cursor.getString(6)).optDouble("recommended_price", 0); }
                catch (Exception ignored) { }
                result.add(new PrivateRideRecord(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5), price, cursor.getString(7)));
            }
        }
        return result;
    }

    public void updatePrivateRideStatus(long id, String status) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        getWritableDatabase().update("private_quotes", values, "id=?", new String[]{Long.toString(id)});
    }

    public String clientReliability(String phone) {
        if (phone == null || phone.isBlank()) return "Sem histórico do telefone";
        int completed = scalarInt("SELECT COUNT(*) FROM private_quotes WHERE phone=? AND status='CONCLUÍDA'", new String[]{phone});
        int cancelled = scalarInt("SELECT COUNT(*) FROM private_quotes WHERE phone=? AND status='CANCELADA'", new String[]{phone});
        int noShow = scalarInt("SELECT COUNT(*) FROM private_quotes WHERE phone=? AND status='NO_SHOW'", new String[]{phone});
        return "Histórico: " + completed + " concluída(s), " + cancelled + " cancelada(s), " + noShow + " não compareceu";
    }

    public void insertStrategy(StrategyAdvice advice, String source) {
        ContentValues values = new ContentValues();
        values.put("session_id", activeSessionId());
        values.put("created_at", System.currentTimeMillis());
        values.put("source", source);
        values.put("action", advice.action.name());
        values.put("title", advice.title);
        values.put("explanation", advice.explanation);
        values.put("projected_gross", advice.projectedFinalGross);
        values.put("confidence", advice.confidence);
        getWritableDatabase().insert("strategy_events", null, values);
    }

    public void insertDynamicObservation(String level, double warmRatio) {
        ContentValues values = new ContentValues();
        values.put("session_id", activeSessionId());
        values.put("created_at", System.currentTimeMillis());
        values.put("level", level);
        values.put("warm_ratio", warmRatio);
        values.put("source", "SCREENSHOT_TEMPORARIO");
        getWritableDatabase().insert("dynamic_observations", null, values);
    }

    public DashboardStats dashboardStats() {
        long id = 0, start = 0, activeGross = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,start_at,gross_cents FROM work_sessions WHERE end_at IS NULL ORDER BY id DESC LIMIT 1", null)) {
            if (cursor.moveToFirst()) { id = cursor.getLong(0); start = cursor.getLong(1); activeGross = cursor.getLong(2); }
        }
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long today = midnight.getTimeInMillis();
        long tomorrow = today + 24L * 3_600_000L;
        long gross = scalarLong("SELECT COALESCE(SUM(gross_cents),0) FROM work_sessions WHERE start_at>=? AND start_at<?",
                new String[]{Long.toString(today), Long.toString(tomorrow)});
        int priorMinutes = scalarInt("SELECT COALESCE(SUM((end_at-start_at)/60000),0) FROM work_sessions WHERE start_at>=? AND start_at<? AND end_at IS NOT NULL",
                new String[]{Long.toString(today), Long.toString(tomorrow)});
        long hourAgo = System.currentTimeMillis() - 3_600_000L;
        int offers = scalarInt("SELECT COUNT(*) FROM offers WHERE session_id=? AND captured_at>=?", new String[]{Long.toString(id), Long.toString(hourAgo)});
        int viable = scalarInt("SELECT COUNT(*) FROM offers WHERE session_id=? AND captured_at>=? AND decision='ACCEPTABLE'", new String[]{Long.toString(id), Long.toString(hourAgo)});
        long lastOfferAt = scalarLong("SELECT COALESCE(MAX(captured_at),0) FROM offers WHERE session_id=?", new String[]{Long.toString(id)});
        int idle = id <= 0 ? 0 : (int) Math.max(0, (System.currentTimeMillis() - Math.max(start, lastOfferAt)) / 60_000L);
        List<Double> rates = new ArrayList<>();
        long previousAt = 0, previousCents = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT captured_at,gross_cents FROM gross_snapshots WHERE session_id=? ORDER BY captured_at ASC",
                new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                long capturedAt = cursor.getLong(0), currentCents = cursor.getLong(1);
                long minutes = previousAt <= 0 ? 0 : (capturedAt - previousAt) / 60_000L;
                if (minutes >= 15 && minutes <= 240 && currentCents >= previousCents) {
                    rates.add((currentCents - previousCents) / 100.0 * 60.0 / minutes);
                }
                previousAt = capturedAt;
                previousCents = currentCents;
            }
        }
        double robust = rates.isEmpty() ? 0 : RobustStats.median(rates);
        if (robust <= 0 && id > 0 && start > 0 && System.currentTimeMillis() - start >= 3_600_000L) {
            robust = gross / 100.0 * 3_600_000.0 / (System.currentTimeMillis() - start);
        }
        LocationSnapshot location = latestLocation(id);
        double learned = scalarDouble("SELECT COALESCE(expected_gross_hour,0) FROM learning_buckets WHERE bucket_key=?", new String[]{learningKey(location)});
        if (learned <= 0) learned = scalarDouble("SELECT COALESCE(expected_gross_hour,0) FROM learning_buckets WHERE bucket_key='GERAL'", null);
        String zone = location == null ? "Não identificada" : String.format(java.util.Locale.ROOT,
                "grade %.2f, %.2f", Math.round(location.latitude / 0.02) * 0.02,
                Math.round(location.longitude / 0.02) * 0.02);
        boolean unsafe = location != null && isCoordinateUnsafe(location.latitude, location.longitude);
        return new DashboardStats(id, start, gross, activeGross, priorMinutes, offers, viable, idle,
                robust, learned, 0, zone, unsafe);
    }

    public boolean isDestinationUnsafe(String destination) {
        if (destination == null || destination.isBlank()) return false;
        String normalized = normalize(destination);
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT name FROM risk_zones WHERE active=1", null)) {
            while (cursor.moveToNext()) if (normalized.contains(normalize(cursor.getString(0)))) return true;
        }
        return false;
    }

    public boolean isCoordinateUnsafe(double latitude, double longitude) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude,longitude,radius_m FROM risk_zones WHERE active=1 AND latitude IS NOT NULL AND longitude IS NOT NULL", null)) {
            while (cursor.moveToNext()) {
                float[] result = new float[1];
                Location.distanceBetween(latitude, longitude, cursor.getDouble(0), cursor.getDouble(1), result);
                if (result[0] <= cursor.getDouble(2)) return true;
            }
        }
        return false;
    }

    public void addRiskZone(String name, double latitude, double longitude, double radiusMeters) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("radius_m", Math.max(100, radiusMeters));
        values.put("active", 1);
        getWritableDatabase().insertWithOnConflict("risk_zones", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public double[] latestCoordinate() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude,longitude FROM location_points ORDER BY captured_at DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? new double[]{cursor.getDouble(0), cursor.getDouble(1)} : null;
        }
    }

    public boolean isStationaryNow() {
        long recent = System.currentTimeMillis() - 120_000L;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT speed FROM location_points WHERE captured_at>=? ORDER BY captured_at DESC LIMIT 1",
                new String[]{Long.toString(recent)})) {
            return cursor.moveToFirst() && cursor.getDouble(0) >= 0 && cursor.getDouble(0) <= 1.5;
        }
    }

    public List<String> riskZoneSummaries() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT name,latitude,longitude,radius_m FROM risk_zones WHERE active=1 ORDER BY name", null)) {
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (!cursor.isNull(1)) value += String.format(java.util.Locale.forLanguageTag("pt-BR"), " (raio %.0fm)", cursor.getDouble(3));
                result.add(value);
            }
        }
        return result;
    }

    public String latestDynamicLevel() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT level FROM dynamic_observations ORDER BY created_at DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : "Não lido";
        }
    }

    public JSONObject exportData() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "rota500-backup-v1");
        root.put("exported_at", System.currentTimeMillis());
        for (String table : BACKUP_TABLES) {
            root.put(table, tableAsJson(table));
        }
        return root;
    }

    public synchronized void restoreData(JSONObject backup) throws Exception {
        if (!"rota500-backup-v1".equals(backup.optString("format"))) {
            throw new IllegalArgumentException("Formato do backup desconhecido");
        }
        for (String table : BACKUP_TABLES) if (backup.optJSONArray(table) == null) {
            throw new IllegalArgumentException("Backup incompleto: " + table);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : BACKUP_TABLES) db.delete(table, null, null);
            for (String table : BACKUP_TABLES) {
                JSONArray rows = backup.getJSONArray(table);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    for (java.util.Iterator<String> keys = row.keys(); keys.hasNext();) {
                        String key = keys.next();
                        Object value = row.get(key);
                        if (value == JSONObject.NULL) values.putNull(key);
                        else if (value instanceof Integer || value instanceof Long) values.put(key, ((Number) value).longValue());
                        else if (value instanceof Number) values.put(key, ((Number) value).doubleValue());
                        else values.put(key, String.valueOf(value));
                    }
                    db.insertOrThrow(table, null, values);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private JSONArray tableAsJson(String table) throws Exception {
        JSONArray array = new JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM " + table, null)) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    String name = cursor.getColumnName(i);
                    switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(i));
                        case Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(i));
                        case Cursor.FIELD_TYPE_STRING -> row.put(name, cursor.getString(i));
                        case Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL);
                        default -> row.put(name, "");
                    }
                }
                array.put(row);
            }
        }
        return array;
    }

    private void updateLearningBucket(String key, double sample) {
        int count = scalarInt("SELECT COALESCE(samples,0) FROM learning_buckets WHERE bucket_key=?", new String[]{key});
        double current = scalarDouble("SELECT COALESCE(expected_gross_hour,0) FROM learning_buckets WHERE bucket_key=?", new String[]{key});
        double next = count == 0 ? sample : current + (sample - current) / Math.min(count + 1.0, 20.0);
        ContentValues values = new ContentValues();
        values.put("bucket_key", key);
        values.put("samples", count + 1);
        values.put("expected_gross_hour", next);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("learning_buckets", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void insertGrossSnapshot(long sessionId, long cents, long capturedAt) {
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("captured_at", capturedAt);
        values.put("gross_cents", cents);
        getWritableDatabase().insert("gross_snapshots", null, values);
    }

    private String currentLearningKey() { return learningKey(latestLocation(activeSessionId())); }

    private String learningKey(LocationSnapshot location) {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int hourBand = calendar.get(Calendar.HOUR_OF_DAY) / 3;
        if (location == null) return "T" + day + "-" + hourBand + "-SEM_LOCAL";
        long latGrid = Math.round(location.latitude / 0.02);
        long lonGrid = Math.round(location.longitude / 0.02);
        return "T" + day + "-" + hourBand + "-G" + latGrid + ":" + lonGrid;
    }

    private LocationSnapshot latestLocation(long sessionId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude,longitude FROM location_points WHERE session_id=? ORDER BY captured_at DESC LIMIT 1", new String[]{Long.toString(sessionId)})) {
            return cursor.moveToFirst() ? new LocationSnapshot(cursor.getDouble(0), cursor.getDouble(1)) : null;
        }
    }

    private int scalarInt(String sql, String[] args) { return (int) scalarLong(sql, args); }
    private long scalarLong(String sql, String[] args) {
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) { return cursor.moveToFirst() ? cursor.getLong(0) : 0; }
    }
    private double scalarDouble(String sql, String[] args) {
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) { return cursor.moveToFirst() ? cursor.getDouble(0) : 0; }
    }

    private String normalize(String value) {
        String plain = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(java.util.Locale.ROOT);
    }

    private JSONObject quoteInputJson(QuoteInput input) {
        JSONObject json = new JSONObject();
        try {
            json.put("pickup_km", input.pickupKm).put("passenger_km", input.passengerKm).put("return_km", input.returnKm)
                    .put("pickup_minutes", input.pickupMinutes).put("passenger_minutes", input.passengerMinutes)
                    .put("return_minutes", input.returnMinutes).put("waiting_minutes", input.waitingMinutes)
                    .put("operating_cost_km", input.operatingCostPerKm).put("desired_net_hour", input.desiredNetPerHour)
                    .put("expected_app_net_hour", input.expectedAppNetPerHour).put("tolls_parking", input.tollsAndParking)
                    .put("margin_percent", input.marginPercent);
        } catch (Exception ignored) { }
        return json;
    }

    private JSONObject quoteResultJson(QuoteResult result) {
        JSONObject json = new JSONObject();
        try {
            json.put("estimated_cost", result.estimatedCost).put("operational_floor", result.operationalFloor)
                    .put("opportunity_floor", result.opportunityFloor).put("recommended_price", result.recommendedPrice)
                    .put("estimated_net", result.estimatedNet).put("estimated_net_hour", result.estimatedNetPerHour);
        } catch (Exception ignored) { }
        return json;
    }

    private static final class LocationSnapshot {
        final double latitude, longitude;
        LocationSnapshot(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
    }
}
