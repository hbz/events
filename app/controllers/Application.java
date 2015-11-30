package controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.*;
import play.mvc.*;
import play.mvc.BodyParser.Json;
import views.html.*;

public class Application extends Controller {

	public Result moved(String path) {
		return movedPermanently("/" + path);
	}

	public Result index() {
		return ok(index.render("POST or GET events: /events/:type"));
	}

	public Result getEvents(String type) {
		String message = String.format("GET events: %s", type);
		Logger.info(message);
		return ok(message);
	}

	@BodyParser.Of(Json.class)
	public Result addEvent(String type) {
		Logger.info(String.format("POST event, type: %s", type));
		try {
			JsonNode jsonNode = request().body().asJson();
			Logger.info(new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(jsonNode));
			return ok(jsonNode);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return internalServerError(e.getMessage());
		}
	}

}
