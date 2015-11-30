package controllers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.*;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.*;
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
	 * @return A backup for the given event type
	 */
	public Result getBackup(String type) {
		Path path = backupPathForType(type);
		try {
			response().setContentType("application/json");
			return ok(prettyPrint(Json.parse(path.toUri().toURL().openStream())));
		} catch (Exception e) {
			e.printStackTrace();
			return internalServerError(e.getMessage());
		}
	}

	/**
	 * @param type The event type
	 * @return The posted JSON request, if we processed it
	 */
	@BodyParser.Of(play.mvc.BodyParser.Json.class)
	public Result addEvent(String type) {
		JsonNode jsonRequestBody = request().body().asJson();
		String headerString = request().headers().keySet().stream()
				.map(k -> String.format("%s: %s", k, request().getHeader(k)))
				.collect(Collectors.joining(", "));
		Logger.debug("POST event, type: {}, headers: {}, body: {}", type,
				headerString, jsonRequestBody);
		try {
			return ok(processRequest(jsonRequestBody, type));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return internalServerError(e.getMessage());
		}
	}

	private static JsonNode processRequest(JsonNode jsonNode, String type)
			throws JsonProcessingException {
		String gitHubEventType = request().getHeader("X-GitHub-Event");
		if (gitHubEventType != null) {
			switch (gitHubEventType) {
			case "issues":
				String jsonString = prettyPrint(jsonNode);
				Logger.info("GitHub issues event: \n{}", jsonString);
				backupIssues(jsonNode, type);
				break;
			default:
				Logger.info("Unhandeled X-GitHub-Event: {}", gitHubEventType);
				break;
			}
		}
		return jsonNode;
	}

	private static String prettyPrint(JsonNode jsonNode)
			throws JsonProcessingException {
		return new ObjectMapper().writerWithDefaultPrettyPrinter()
				.writeValueAsString(jsonNode);
	}

	private static void backupIssues(JsonNode jsonNode, String type) {
		String backupUrl = jsonNode.get("repository").get("issues_url").textValue()
				.replace("{/number}", "");
		WS.url(backupUrl).setQueryParameter("state", "all").get().map(response -> {
			if (response.getStatus() == OK) {
				JsonNode json = response.asJson();
				Logger.debug("Backup: {}", json.toString());
				Path path = backupPathForType(type);
				try (BufferedWriter writer = Files.newBufferedWriter(path)) {
					writer.write(json.toString());
					Logger.info("Wrote backup to: {}", path.toAbsolutePath());
				} catch (Exception e) {
					e.printStackTrace();
					Logger.error("Could not write to: {}", path);
				}
				return json;
			}
			Logger.warn("Failed backup request to URL: {}", backupUrl);
			return null;
		});
	}

	private static Path backupPathForType(String type) {
		String basePath = Play.application().path().getAbsolutePath();
		Path path = Paths.get(String.format("%s/backup-%s.json", basePath, type));
		return path;
	}

}
