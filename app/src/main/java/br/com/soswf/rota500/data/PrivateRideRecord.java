package br.com.soswf.rota500.data;

public final class PrivateRideRecord {
    public final long id;
    public final String client;
    public final String phone;
    public final String scheduledAt;
    public final String origin;
    public final String destination;
    public final double price;
    public final String status;

    public PrivateRideRecord(long id, String client, String phone, String scheduledAt,
            String origin, String destination, double price, String status) {
        this.id = id;
        this.client = client;
        this.phone = phone;
        this.scheduledAt = scheduledAt;
        this.origin = origin;
        this.destination = destination;
        this.price = price;
        this.status = status;
    }
}
