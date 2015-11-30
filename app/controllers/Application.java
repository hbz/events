package controllers;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.*;
import play.mvc.*;
import play.mvc.BodyParser.Json;
import views.html.*;

/**
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	/**
	 * @param path The path to redirect to
	 * @return A 301 MOVED_PERMANENTLY result
	 */
	public Result moved(String path) {
		return movedPermanently("/" + path);
	}

	/**
	 * @return TODO An overview documentation, or all events as RSS
	 */
	public Result index() {
		return ok(index.render("POST or GET events: /events/:type"));
	}

	/**
	 * @param type The event type
	 * @return TODO All events of the given type as RSS, see
	 *         https://github.com/hbz/events/issues/3
	 */
	public Result getEvents(String type) {
		String message = String.format("GET events: %s", type);
		Logger.info(message);
		return ok(message);
	}

	/**
	 * @param type The event type
	 * @return The posted JSON request, if we processed it
	 */
	@BodyParser.Of(Json.class)
	public Result addEvent(String type) {
		JsonNode jsonRequestBody = request().body().asJson();
		String headerString = request().headers().keySet().stream()
				.map(k -> String.format("%s: %s", k, request().getHeader(k)))
				.collect(Collectors.joining(", "));
		Logger.debug("POST event, type: {}, headers: {}, body: {}", type,
				headerString, jsonRequestBody);
		try {
			return ok(processRequest(jsonRequestBody));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return internalServerError(e.getMessage());
		}
	}

	private static JsonNode processRequest(JsonNode jsonNode)
			throws JsonProcessingException {
		String gitHubEventType = request().getHeader("X-GitHub-Event");
		if (gitHubEventType != null) {
			switch (gitHubEventType) {
			case "issues":
				String jsonString = new ObjectMapper().writerWithDefaultPrettyPrinter()
						.writeValueAsString(jsonNode);
				Logger.info("GitHub issues event: \n{}", jsonString);
				break;
			default:
				Logger.info("X-GitHub-Event: {}", gitHubEventType);
				break;
			}
		}
		return jsonNode;
	}

}
