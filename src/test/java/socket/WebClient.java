package socket;

import java.net.URISyntaxException;
import java.util.Map;

public class WebClient {
    private Client client;
    private WebClient(){

    }
    public static WebClient getInstance(){
        return new WebClient();
    }
    public void connectToSocket(SocketContext context) {
        boolean isBodySend = false;
        try {
            client = new Client(context);
            if (!context.getRequestHeaders().isEmpty()) {
                final Map<String, String> requestHeaderParams = context.getRequestHeaders();
                requestHeaderParams.forEach((key, value) -> {
                    client.addHeader(key, value);
                });
            }
            client.connectBlocking();
            while (!client.isClosed()) {
                if (client.getAliveTime() >= context.getTimeOut()) {
                    client.close(1006, "Time Out");
                }
                if (context.getRunnable() != null) {
                    context.getRunnable().run();
                }
                if (context.getBody() != null && isBodySend) {
                    client.send(context.getBody());
                    isBodySend = true;
                }
                if (context.getExpectedMessage() != null) {
                    client.onMessage(context.getExpectedMessage());
                    return;
                }
            }
        } catch (URISyntaxException | InterruptedException e){
            throw new RuntimeException(e);
        }
    }
}
