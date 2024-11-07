package com.vityazev_egor.Core;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import com.vityazev_egor.Core.WaitTask.IWaitTask;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

@ClientEndpoint
public class WebSocketClient {
    private Session session;
    private final CustomLogger logger = new CustomLogger(WebSocketClient.class.getName());
    private final CopyOnWriteArrayList<SocketMessage> messages = new CopyOnWriteArrayList<>();
    private final CommandsProcessor cmdProcessor = new CommandsProcessor();

    public enum messageType{
        undefinded,
        jsResult
    }

    private class SocketMessage{
        public messageType type;
        public String message;
        public long creationTime;
        public Integer id;

        public SocketMessage(Integer id, messageType type, String message){
            this.type = type;
            this.message = message;
            this.creationTime = System.currentTimeMillis();
            this.id = id;
        }
    }

    public WebSocketClient(String url) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, new URI(url));
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        logger.info("Connected to the server");
    }

    @OnMessage
    public void onMessage(String message) {

        messageType type = detectMessageType(message);
        Optional<Integer> messageId = cmdProcessor.parseIdFromCommand(message);
        if (messageId.isPresent()){
            messages.add(new SocketMessage(messageId.get(), type, message));
        }
        logger.info("Received message with type = " +type +", content = " + message);
        if (messages.size()>=10){
            messages.remove(0);
        }
    }

    private messageType detectMessageType(String message){
        if (cmdProcessor.getJsResult(message) != null){
            return messageType.jsResult;
        }
        else{
            return messageType.undefinded;
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error occurred: " + throwable.getMessage());
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.warning("Connection closed: " + closeReason);
    }

    public void sendCommand(String json) {
        //System.out.println(json);
        try{
            session.getAsyncRemote().sendText(json);
        }
        catch (Exception ex){
            logger.warning("Error in sendCommand method");
            ex.printStackTrace();
        }
    }

    public void sendCommand(String[] jsons){
        for (String json : jsons) {
            sendCommand(json);
        }
    }

    public Optional<String> sendAndWaitResult(Integer timeOutSeconds, String json){
        Optional<Integer> messageId = cmdProcessor.parseIdFromCommand(json);
        if (!messageId.isPresent()) return Optional.empty();

        var task = new WaitTask(
            new IWaitTask() {
                @Override
                public Boolean execute() {
                    var filtered = messages.stream().anyMatch(m->m.id == messageId.get());
                    return filtered;
                } 
            }
        );
        sendCommand(json);
        var result = task.execute(timeOutSeconds, 50);
        if (result){
            return Optional.of(messages.stream().filter(m-> m.id == messageId.get()).findFirst().get().message);
        }
        else{
            return Optional.empty();
        }
    }
    
}
