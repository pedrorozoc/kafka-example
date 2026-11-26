package bistro.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    public void notifyConfirmed(String to, UUID reservationCode, String tableNumber){

        SimpleMailMessage msg= new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Tu reserva " + reservationCode +" está confirmada");
        msg.setText("Te asignamos la mesa "+tableNumber +". Te esperamos!. ");

        mailSender.send(msg);
        log.info("Aviso de Confirmación enviado a {} (reserva{})", to, reservationCode);
    }

    public void notifyRejected(String to, UUID reservatiionCode, String reason){

        SimpleMailMessage msg= new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Tu reserva "+ reservatiionCode + "fue rechazada");
        msg.setText("Tu reserva "+ reservatiionCode + "fue rechazada debido a que "+ reason);

        mailSender.send(msg);
        log.info("Aviso de Rechazo enviado a {} (reserva{})", to, reservatiionCode);

    }

}
