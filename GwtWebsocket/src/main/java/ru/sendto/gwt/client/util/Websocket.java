package ru.sendto.gwt.client.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;

import org.fusesource.restygwt.client.JsonEncoderDecoder;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

import ru.sendto.dto.Dto;

/**
 * Класс реализующий вэбсокет.
 */
public class Websocket {
	
	static List<Consumer<Dto>> filters = new ArrayList<>();

	
	
	/**
	 * Интерфейс для обратного вызова прихода текстовых сообщений.
	 */
	@FunctionalInterface
	public static interface IDtoMessageCallback<T extends Dto> {
		public void onMessage(T dto);
	}

	/**
	 * Интерфейс для обратного вызова прихода бинарных сообщений.
	 */
	public static interface IBynaryMessageCallback {
		default public void onMessage(ArrayBuffer b) {
		};
	}

	public static class ConnectedEvent{};
	public static class DisconnectedEvent{};

	/**
	 * Очередь сообщений, которые ожидают отправки
	 */
	static LinkedList<Dto> queueToSend = new LinkedList<>();

	// Объект вэбсокета.
	static JavaScriptObject ws;
	static boolean connected = false;

	// Для преобразования данных dto.
	static interface Codec extends JsonEncoderDecoder<Dto> {
	};

	static Codec codec = null;
	// Список обработчиков dto.
	static Map<String, List<IDtoMessageCallback>> callbackDtoList = new HashMap();
	// Список обработчиков бинарных данных.
	static List<IBynaryMessageCallback> callbackBynaryList = new ArrayList<IBynaryMessageCallback>();

	/**
	 * Инициализация подключения.
	 * 
	 * @param url
	 *            - строка подключения.
	 */
	public static void init(String url, Consumer<Dto>...filters) {
		Websocket.filters.addAll(Arrays.asList(filters));
		codec = GWT.create(Codec.class);
		url = url.replaceFirst("http", "ws");
		connect(url);
	}

	/**
	 * Добавление обработчиков событий.
	 * 
	 * @param type
	 *            - тип dto входных данных.
	 * @param callback
	 *            - обработчик данных.
	 */
	public static <T extends Dto> void addMessageListener(Class<T> type, IDtoMessageCallback<T> callback) {
		List<IDtoMessageCallback> list = callbackDtoList.get(type.getName());
		if (list == null) {
			list = new ArrayList<>();
			callbackDtoList.put(type.getName(), list);
		}
		list.add(callback);
	}

	/**
	 * Добавление обработчиков событий (бинарные данные).
	 * 
	 * @param callback
	 *            - обработчик данных.
	 */
	public static void addMessageListener(IBynaryMessageCallback callback) {
		callbackBynaryList.add(callback);
	}

	/**
	 * Отправка данных.
	 * 
	 * @param dto
	 *            - отправляемые данные.
	 */
	public static void send(Dto dto) {
		filters.forEach(c->c.accept(dto));
		if (connected) {
			send(codec.encode(dto).toString());
		} else {
			queueToSend.add(dto);
		}
	}
	/**
	 * Отправка текстовых данных.
	 * 
	 * @param msg
	 *            - текстовые данные.
	 */
	private static native void send(String msg)/*-{
		@ru.sendto.gwt.client.util.Websocket::ws.send(msg);
	}-*/;

	/**
	 * Подключение соекта к заданному url.
	 * 
	 * @param url
	 *            - куда подключаемся.
	 */
	private static native void connect(String url) /*-{
		@ru.sendto.gwt.client.util.Websocket::ws = new WebSocket(url);
		@ru.sendto.gwt.client.util.Websocket::ws.binaryType = "arraybuffer";
		@ru.sendto.gwt.client.util.Websocket::ws.onmessage = function(e) {
			if (typeof e.data === "string")
				@ru.sendto.gwt.client.util.Websocket::receiv(*)(e.data);
			else
				@ru.sendto.gwt.client.util.Websocket::receivBin(*)(e.data);
		}
		@ru.sendto.gwt.client.util.Websocket::ws.onopen = function() {
			@ru.sendto.gwt.client.util.Websocket::connected = true;
			@ru.sendto.gwt.client.util.Websocket::reconnectCount = 0;
			@ru.sendto.gwt.client.util.Websocket::onOpen()();
		}
		@ru.sendto.gwt.client.util.Websocket::ws.onclose = function() {
			console.log("clodsed");
			if (@ru.sendto.gwt.client.util.Websocket::connected)
				@ru.sendto.gwt.client.util.Websocket::reconnect(Ljava/lang/String;I)(url,5000);
			@ru.sendto.gwt.client.util.Websocket::connected = false;

		}
		@ru.sendto.gwt.client.util.Websocket::ws.onerror = function(e) {
			console.log("error:" + e.data);
			//			if(@ru.sendto.gwt.client.util.Websocket::connected)
			@ru.sendto.gwt.client.util.Websocket::reconnect(Ljava/lang/String;I)(url,5000);
			@ru.sendto.gwt.client.util.Websocket::connected = false;

		}
	}-*/;

	/**
	 * Вызывается автоматически при открытии соединения Отправляет сообщения,
	 * которые ожидали отправки.
	 */
	public static void onOpen() {
		Bus.get().fire(new ConnectedEvent());
		ListIterator<Dto> iter = queueToSend.listIterator();
		for (; iter.hasNext();) {
			send(iter.next());
			iter.remove();
		}
	}

	private static int reconnectCount = 0;
	static elemental.html.Notification reconnectNotification;

	private static void reconnect(String url, int delay) {
		reconnectCount++;

		Bus.get().fire(new DisconnectedEvent());
		Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {

			@Override
			public boolean execute() {
				connect(url);
				return false;
			}
		}, delay/* +(reconnectCount>3?10_000:1_000) */);
	}

	/**
	 * Обработка текстовых данных.
	 * 
	 * @param self
	 *            - ссылка на данный класс.
	 * @param m
	 *            - строка сообщения.
	 */
	private static void receiv(String m) {
		Dto dto = codec.decode(m);
		List<IDtoMessageCallback> callList = callbackDtoList.get(dto.getClass().getName());
		if(callList!=null){
			for (IDtoMessageCallback call : callList) {
				call.onMessage(dto);
			}
		}
	}

	/**
	 * Обработка бинарных данных.
	 * 
	 * @param self
	 *            - ссылка на данный класс.
	 * @param b
	 *            - массив байт.
	 */
	private static void receivBin(ArrayBuffer b) {
		for (IBynaryMessageCallback c : callbackBynaryList)
			c.onMessage(b);
	}

}
