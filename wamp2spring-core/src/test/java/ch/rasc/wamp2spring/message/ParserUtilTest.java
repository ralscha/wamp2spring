/**
 * Copyright 2017-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.wamp2spring.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ParserUtilTest {

	@SuppressWarnings("resource")
	@Test
	public void testReadArray() throws JsonParseException, IOException {
		ObjectMapper om = new ObjectMapper();
		JsonParser jp = om.getFactory().createParser("\"test\"");
		jp.nextToken();
		assertThat(ParserUtil.readArray(jp)).isNull();

		jp = om.getFactory().createParser("[1,2,3]");
		jp.nextToken();
		assertThat(ParserUtil.readArray(jp)).containsExactly(1, 2, 3);

		jp = om.getFactory().createParser("[[\"k\",\"l\",\"m\"],[\"a\",\"b\",\"c\"]]");
		jp.nextToken();
		assertThat(ParserUtil.readArray(jp)).containsExactly(Arrays.asList("k", "l", "m"),
				Arrays.asList("a", "b", "c"));
	}

	@SuppressWarnings({ "resource", "unchecked" })
	@Test
	public void testReadObject() throws JsonParseException, IOException {
		ObjectMapper om = new ObjectMapper();
		JsonParser jp = om.getFactory().createParser("\"test\"");
		jp.nextToken();
		assertThat(ParserUtil.readArray(jp)).isNull();

		jp = om.getFactory().createParser("{\"key1\":1,\"key2\":2}");
		jp.nextToken();
		assertThat(ParserUtil.readObject(jp)).containsOnly(MapEntry.entry("key1", 1),
				MapEntry.entry("key2", 2));

		jp = om.getFactory().createParser("{\"keyA\":1.1,\"keyB\":2.2}");
		jp.nextToken();
		assertThat(ParserUtil.readObject(jp)).containsOnly(MapEntry.entry("keyA", 1.1),
				MapEntry.entry("keyB", 2.2));

		jp = om.getFactory().createParser("{\"k1\":\"one\",\"k2\":\"two\"}");
		jp.nextToken();
		assertThat(ParserUtil.readObject(jp)).containsOnly(MapEntry.entry("k1", "one"),
				MapEntry.entry("k2", "two"));

		jp = om.getFactory().createParser("{\"k1\":true,\"k2\":false, \"k3\":null}");
		jp.nextToken();
		assertThat(ParserUtil.readObject(jp)).containsOnly(MapEntry.entry("k1", true),
				MapEntry.entry("k2", false), MapEntry.entry("k3", null));

		jp = om.getFactory().createParser(
				"{\"o1\":{\"a1\":1,\"a2\":2},\"o2\":{\"b1\":11,\"b2\":22},\"o3\":{\"c1\":111,\"c2\":222}}");
		jp.nextToken();
		Map<String, Object> m = ParserUtil.readObject(jp);
		assertThat(m).containsKeys("o1", "o2", "o3");
		assertThat((Map<String, Object>) m.get("o1"))
				.containsOnly(MapEntry.entry("a1", 1), MapEntry.entry("a2", 2));
		assertThat((Map<String, Object>) m.get("o2"))
				.containsOnly(MapEntry.entry("b1", 11), MapEntry.entry("b2", 22));
		assertThat((Map<String, Object>) m.get("o3"))
				.containsOnly(MapEntry.entry("c1", 111), MapEntry.entry("c2", 222));
	}

}
