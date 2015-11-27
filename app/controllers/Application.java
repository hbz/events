package controllers;

import com.fasterxml.jackson.databind.JsonNode;

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
		JsonNode json = request().body().asJson();
		String message =
				String.format("POST event, type: %s, content: %s", type, json);
		Logger.info(message);
		return ok(message);
	}

}
