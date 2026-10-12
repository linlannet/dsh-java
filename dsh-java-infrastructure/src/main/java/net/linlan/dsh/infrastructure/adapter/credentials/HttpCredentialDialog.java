package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.authorization.adapter.port.ICredentialDialogPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Scanner;

@Component
public class HttpCredentialDialog implements ICredentialDialogPort {

    @Override
    public Optional<String> promptCredential(String service, String scope) {
        String prompt = String.format("[credential] service=%s scope=%s: ", service, scope);
        System.out.print(prompt);
        System.out.flush();
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(line);
        }
        return Optional.empty();
    }
}
