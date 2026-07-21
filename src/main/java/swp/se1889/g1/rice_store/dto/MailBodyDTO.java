package swp.se1889.g1.rice_store.dto;

public class MailBodyDTO {
    private String to;
    private String subject;
    private String text;

    public MailBodyDTO() {
    }

    // Constructor để khởi tạo đối tượng
    public MailBodyDTO(String to, String subject, String text) {
        this.to = to;
        this.subject = subject;
        this.text = text;
    }

    // Getters
    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public String getText() {
        return text;
    }

    // Setters
    public void setTo(String to) {
        this.to = to;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setText(String text) {
        this.text = text;
    }
}

