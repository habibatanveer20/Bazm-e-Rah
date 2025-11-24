package com.example.bazmeraah;

public class ChatMessage {
    public String key;       // firebase node key (optional, useful later)
    public String contact;   // phone
    public String from;      // "admin" or null / "user"
    public String message;   // message text (or reply text)
    public long timestamp;   // numeric timestamp

    public ChatMessage() { }

    public ChatMessage(String key, String contact, String from, String message, long timestamp) {
        this.key = key;
        this.contact = contact;
        this.from = from;
        this.message = message;
        this.timestamp = timestamp;
    }
}
