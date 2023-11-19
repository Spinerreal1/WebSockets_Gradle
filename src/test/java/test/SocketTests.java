package test;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import model.SocketMessageModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import socket.SocketContext;
import model.SubscribeModel;
import socket.WebClient;
import java.util.Random;

import static com.codeborne.selenide.Selenide.$x;
import static io.restassured.RestAssured.given;
import static com.codeborne.selenide.Selenide.open;

public class SocketTests {
    private SocketContext context;

    private String getRandomId(){
        int leftLimit = 97;   // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLenght = 7;
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLenght)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
    private String getSocketConnectionUrl(){
        JsonPath response = given()
                .post("https://api.kucoin.com/api/v1/bullet-public")
                .then().log().body().extract()
                .response().jsonPath();
        String token = response.getString("data.token");
        String socketBaseEndpoint = response.getString("data.instanceServers[0].endpoint");
        return socketBaseEndpoint + "?token=" + token + "&connectId=" + getRandomId();

    }

    @Test
    public void socketUI_ReceiveText(){
        Configuration.browser = "chrome";
        open("https://www.piesocket.com/websocket-tester");
        String url = $x("//input[@id='email']").getValue();
        $x("//button[@type='submit']").click();
        String expectedMessage = "Message from site";
        context = new SocketContext();
        context.setURI(url);
        context.setBody(expectedMessage);
        context.setExpectedMessage(expectedMessage);
        context.setTimeOut(5);

        WebClient.getInstance().connectToSocket(context);
        $x("//*[@id='consoleLog']")
                .shouldHave(Condition.partialText(context.getBody()));
    }

    @Test
    public void socketUI_SendText() {
        Configuration.browser = "chrome";
        open("https://www.piesocket.com/websocket-tester");
        SelenideElement input = $x("//input[@id='email']");
        SelenideElement button = $x("//button[@type='submit']");
        String expectedMessage = "ThreadQA Message";

        String url = input.getValue();
        button.click();

        Runnable sendUIMessage = new Runnable() {
            @Override
            public void run() {
                input.clear();
                input.sendKeys(expectedMessage);
                button.click();
            }
        };
        context.setURI(url);
        context.setExpectedMessage(expectedMessage);
        context.setTimeOut(5);
        context.setRunnable(sendUIMessage);

        WebClient.getInstance().connectToSocket(context);
        Assertions.assertNotEquals(context.getStatusCode(), 1006, "Expected message not received");
    }

    @Test
    public void socketKucoin() throws JsonProcessingException {

        SubscribeModel subscribeModel = new SubscribeModel();
        subscribeModel.setId(Math.abs(new Random().nextInt()));
        subscribeModel.setResponse(true);
        subscribeModel.setType("subscribe");
        subscribeModel.setTopic("/market/ticker:BTC-USDT");
        subscribeModel.setPrivateChannel(false);
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(subscribeModel);

        context = new SocketContext();
        context.setTimeOut(5);
        context.setBody(json);
        context.setURI(getSocketConnectionUrl());
        WebClient.getInstance().connectToSocket(context);

        String firstNormalMessage = context.getMessageList().stream().filter(x->x.contains("\"type\":\"message\""))
                .findFirst().orElseThrow(()-> new RuntimeException("No normal message found"));
        SocketMessageModel messageOne = objectMapper.readValue(firstNormalMessage, SocketMessageModel.class);

        String lastNormalMessage = context.getMessageList().get(context.getMessageList().size()-1);
        SocketMessageModel lastMessage = objectMapper.readValue(lastNormalMessage, SocketMessageModel.class);
        Assertions.assertNotEquals(messageOne.getData().getSequence(), lastMessage.getData().getSequence());
    }
}
