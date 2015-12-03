package controllers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.*;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.oauth.OAuth;
import play.libs.oauth.OAuth.ConsumerKey;
import play.libs.oauth.OAuth.OAuthCalculator;
import play.libs.oauth.OAuth.RequestToken;
import play.libs.ws.WS;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.*;
import views.html.*;

/**
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	private static final Config CONFIG =
			ConfigFactory.parseFile(new File("conf/application.conf")).resolve();
	private static final String GITHUB_AUTH_TOKEN =
			CONFIG.getString("github.auth.token");
	private static final String GITHUB_USER_AGENT =
			CONFIG.getString("github.user.agent");

	private static final int SHORT_LINK_LENGTH =
			"https://t.co/DNR4C93fmb".length();

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
				logEvent(jsonNode, gitHubEventType);
				backupIssues(jsonNode, type);
				if (jsonNode.get("action").asText().equals("opened")) {
					tweet(createNewIssueTweetText(jsonNode));
				}
				break;
			case "issue_comment":
				logEvent(jsonNode, gitHubEventType);
				backupIssues(jsonNode, type);
				if (jsonNode.get("action").asText().equals("created")) {
					tweet(createNewCommentTweetText(jsonNode));
				}
				break;
			default:
				Logger.info("Unhandeled X-GitHub-Event: {}", gitHubEventType);
				break;
			}
		}
		return jsonNode;
	}

	private static void logEvent(JsonNode jsonNode, String gitHubEventType)
			throws JsonProcessingException {
		String jsonString = prettyPrint(jsonNode);
		Logger.info("GitHub {} event: \n{}", gitHubEventType, jsonString);
	}

	private static String prettyPrint(JsonNode jsonNode)
			throws JsonProcessingException {
		return new ObjectMapper().writerWithDefaultPrettyPrinter()
				.writeValueAsString(jsonNode);
	}

	private static void backupIssues(JsonNode jsonNode, String type) {
		String issuesUrl = jsonNode.get("repository").get("issues_url").textValue()
				.replace("{/number}", "");
		Logger.debug("Getting issues from: {}", issuesUrl);
		Promise<JsonNode> issues =
				WS.url(issuesUrl).setHeader(USER_AGENT, GITHUB_USER_AGENT)
						.setHeader(AUTHORIZATION, "token " + GITHUB_AUTH_TOKEN)
						.setQueryParameter("state", "all").get().map(response -> {
							if (response.getStatus() == OK) {
								JsonNode json = response.asJson();
								return json;
							}
							Logger.warn("Failed backup issues request to URL: {}", issuesUrl);
							return Json.newArray();
						});
		issues.flatMap(json -> {
			Iterable<JsonNode> iterable = () -> json.elements();
			Stream<JsonNode> stream =
					StreamSupport.stream(iterable.spliterator(), false);
			Stream<Promise<Object>> comments = stream.map(node -> {
				if (node.isObject()) {
					ObjectNode objectNode = (ObjectNode) node;
					String commentsUrl = objectNode.get("comments_url").asText();
					Logger.debug("Getting comments from: {}", commentsUrl);
					return WS.url(commentsUrl).setHeader(USER_AGENT, GITHUB_USER_AGENT)
							.setHeader(AUTHORIZATION, "token " + GITHUB_AUTH_TOKEN).get()
							.map(commentsResponse -> {
						if (commentsResponse.getStatus() == OK) {
							JsonNode commentsJson = commentsResponse.asJson();
							objectNode.set("comments_data", commentsJson);
							return commentsJson;
						}
						Logger.warn("Failed backup comments request to URL: {}",
								commentsUrl);
						return Promise.pure(Json.newArray());
					});
				}
				return Promise.pure(Json.newArray());
			});
			Promise.sequence(comments.collect(Collectors.toList())).onRedeem(vals -> {
				Path path = backupPathForType(type);
				try (BufferedWriter writer = Files.newBufferedWriter(path)) {
					writer.write(json.toString());
					Logger.info("Wrote backup to: {}", path.toAbsolutePath());
				} catch (Exception e) {
					e.printStackTrace();
					Logger.error("Could not write to: {}", path);
				}
			});
			return Promise.pure(Json.newObject());
		});
	}

	private static Path backupPathForType(String type) {
		String basePath = Play.application().path().getAbsolutePath();
		Path path = Paths.get(String.format("%s/backup-%s.json", basePath, type));
		return path;
	}

	private static void tweet(String message) {
		Logger.debug("Plain-text tweet: {}", message);
		String encoded = net.oauth.OAuth.percentEncode(message);
		String content = "status=" + encoded;
		String twitterUpdateUrl =
				"https://api.twitter.com/1.1/statuses/update.json";
		WSRequest request = WS.url(twitterUpdateUrl)
				.setContentType("application/x-www-form-urlencoded")
				.setHeader(USER_AGENT, GITHUB_USER_AGENT).sign(twitterOAuth());
		request.post(content).map(response -> {
			JsonNode json = response.asJson();
			Logger.info("Twitter response: {}", json.toString());
			return json;
		});
	}

	private static OAuthCalculator twitterOAuth() {
		return new OAuthCalculator(
				new ConsumerKey(CONFIG.getString("twitter.auth.consumer.key"),
						CONFIG.getString("twitter.auth.consumer.secret")),
				new RequestToken(CONFIG.getString("twitter.auth.access.token"),
						CONFIG.getString("twitter.auth.access.secret")));
	}

	private static String createNewIssueTweetText(JsonNode jsonNode) {
		String title = jsonNode.get("issue").get("title").asText();
		String url = jsonNode.get("issue").get("html_url").asText();
		String id = jsonNode.get("issue").get("user").get("login").asText();
		String meta = String.format("New issue by %s: ", id);
		String titleToTweet = shortenedIfNeeded(title, meta, 3); // 2 quotes 1 blank
		String tweetText = String.format("%s\"%s\" ", meta, titleToTweet);
		Logger.debug("Tweet length: {}", tweetText.length() + SHORT_LINK_LENGTH);
		return tweetText + url;
	}

	private static String createNewCommentTweetText(JsonNode jsonNode) {
		String url = jsonNode.get("comment").get("html_url").asText();
		String id = jsonNode.get("comment").get("user").get("login").asText();
		String body = jsonNode.get("comment").get("body").asText().trim();
		String meta = String.format(": new comment by %s on ", id);
		String bodyToTweet = shortenedIfNeeded(body, meta, 2); // 2 quotes
		String tweetText = String.format("\"%s\"%s", bodyToTweet, meta);
		Logger.debug("Tweet length: {}", tweetText.length() + SHORT_LINK_LENGTH);
		return tweetText + url;
	}

	private static String shortenedIfNeeded(String text, String meta, int add) {
		int remaining = 140 - SHORT_LINK_LENGTH - meta.length() - add;
		String shortBody = text.substring(0, Math.min(remaining, text.length()));
		boolean shortened = shortBody.length() < text.length();
		String bodyToTweet = shortened
				? (shortBody.substring(0, shortBody.length() - 3) + "...") : text;
		return bodyToTweet;
	}

}
