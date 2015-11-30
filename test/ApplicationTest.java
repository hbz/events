import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.*;

import play.mvc.*;
import play.test.*;
import play.data.DynamicForm;
import play.data.validation.ValidationError;
import play.data.validation.Constraints.RequiredValidator;
import play.i18n.Lang;
import play.libs.F;
import play.libs.F.*;
import play.twirl.api.Content;

import static play.test.Helpers.*;
import static org.junit.Assert.*;

/**
 * https://www.playframework.com/documentation/2.4.x/JavaTest
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertEquals(2, a);
	}

	@Test
	public void renderTemplate() {
		Content html = views.html.index.render("Template test");
		assertEquals("text/html", html.contentType());
		assertTrue(contentAsString(html).contains("Template test"));
	}

}
