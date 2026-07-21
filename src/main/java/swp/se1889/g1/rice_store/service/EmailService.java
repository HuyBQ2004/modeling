package swp.se1889.g1.rice_store.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import swp.se1889.g1.rice_store.dto.MailBodyDTO;

@Service
public class EmailService {
    private final JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public void sendSimpleMessage(MailBodyDTO mailBodyDTO) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(mailBodyDTO.getTo());
        message.setFrom("huybqhe180599@fpt.edu.vn");
        message.setSubject(mailBodyDTO.getSubject());
        message.setText(mailBodyDTO.getText());

        javaMailSender.send(message);
    }
}
