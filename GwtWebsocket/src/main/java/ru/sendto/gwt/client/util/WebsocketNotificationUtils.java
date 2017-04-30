package ru.sendto.gwt.client.util;

import ru.sendto.gwt.client.util.Websocket.ConnectedEvent;
import ru.sendto.gwt.client.util.Websocket.DisconnectedEvent;

public class WebsocketNotificationUtils {

	static String iconConnected = "res/connected.png";
	static String iconDisconected = "res/disconnected.png";
	
	private WebsocketNotificationUtils() {}
	
	public static void init(){};
	
	public static void setIconDisconected(String iconDisconected) {
		iconDisconected = iconDisconected;
	}

	public static void setIconConnected(String iconConnected) {
		iconConnected = iconConnected;
	}

	static{Bus.get().listen(ConnectedEvent.class, WebsocketNotificationUtils::reconnectNotification);}
	static void reconnectNotification(ConnectedEvent event) {
		if (Websocket.reconnectNotification != null) {
			Notifications.close(Websocket.reconnectNotification);
			Websocket.reconnectNotification = null;
			
			Notifications.show("reconnect", "Подключение активно", "Вы снова подключены", WebsocketNotificationUtils.iconConnected, 5000);
			
		}
	}

	static{Bus.get().listen(DisconnectedEvent.class, WebsocketNotificationUtils::disconnectedNotication);}
	static void disconnectedNotication(DisconnectedEvent event) {
		// TODO добавить картиночку для оповещения
		if (Websocket.reconnectNotification == null) {
			Websocket.reconnectNotification = Notifications.show("reconnect", "Подключение нарушено", "Переподключение...", WebsocketNotificationUtils.iconDisconected, 0);
		}
	}

}
