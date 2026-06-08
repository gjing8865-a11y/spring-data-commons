/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Window}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
class WindowUnitTests {

	@Test // GH-2151
	void equalsAndHashCode() {

		IntFunction<OffsetScrollPosition> positionFunction = OffsetScrollPosition.positionFunction(0);
		Window<Integer> one = Window.from(List.of(1, 2, 3), positionFunction);
		Window<Integer> two = Window.from(List.of(1, 2, 3), positionFunction);

		assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
		assertThat(one.equals(two)).isTrue();

		assertThat(Window.from(List.of(1, 2, 3), positionFunction, true)).isNotEqualTo(two).doesNotHaveSameHashCodeAs(two);
	}

	@Test // GH-2151
	void allowsIteration() {

		Window<Integer> window = Window.from(List.of(1, 2, 3), OffsetScrollPosition.positionFunction(0));

		for (Integer integer : window) {
			assertThat(integer).isBetween(1, 3);
		}
	}

	@Test // GH-2151, GH-3070
	void shouldCreateCorrectPositions() {

		Window<Integer> window = Window.from(List.of(1, 2, 3), OffsetScrollPosition.positionFunction(0));

		assertThat(window.positionAt(0)).isEqualTo(ScrollPosition.offset(0));
		assertThat(window.positionAt(window.size() - 1)).isEqualTo(ScrollPosition.offset(2));

		// by index
		assertThat(window.positionAt(1)).isEqualTo(ScrollPosition.offset(1));

		// by object
		assertThat(window.positionAt(Integer.valueOf(1))).isEqualTo(ScrollPosition.offset(0));
	}

	@Test
	void windowTakesSnapshotAgainstSourceListAdd() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.size()).isEqualTo(3);
		source.add("d");
		assertThat(window.size()).isEqualTo(3);
		assertThat(window.getContent()).containsExactly("a", "b", "c");
	}

	@Test
	void windowTakesSnapshotAgainstSourceListRemove() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.size()).isEqualTo(3);
		source.remove(0);
		assertThat(window.size()).isEqualTo(3);
		assertThat(window.getContent()).containsExactly("a", "b", "c");
	}

	@Test
	void windowTakesSnapshotAgainstSourceListSet() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.getContent().get(0)).isEqualTo("a");
		source.set(0, "z");
		assertThat(window.getContent().get(0)).isEqualTo("a");
	}

	@Test
	void windowTakesSnapshotAgainstSourceListClear() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.size()).isEqualTo(3);
		source.clear();
		assertThat(window.size()).isEqualTo(3);
		assertThat(window.getContent()).containsExactly("a", "b", "c");
	}

	@Test
	void windowContentRejectsAddRemoveSetClear() {

		Window<String> window = Window.from(new ArrayList<>(List.of("a", "b", "c")),
				OffsetScrollPosition.positionFunction(0));
		List<String> content = window.getContent();

		assertThatThrownBy(() -> content.add("d")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> content.remove(0)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> content.set(0, "z")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> content.clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void windowIteratorRejectsRemove() {

		Window<String> window = Window.from(List.of("a", "b", "c"), OffsetScrollPosition.positionFunction(0));
		Iterator<String> it = window.iterator();

		it.next();
		assertThatThrownBy(it::remove).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void positionAtUsesConstructionSnapshot() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.positionAt(0)).isEqualTo(ScrollPosition.offset(0));
		source.add("d");
		assertThat(window.positionAt(2)).isEqualTo(ScrollPosition.offset(2));
	}

	@Test
	void positionAtObjectUsesConstructionSnapshot() {

		List<String> source = new ArrayList<>(List.of("a", "b", "c"));
		Window<String> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		assertThat(window.positionAt("b")).isEqualTo(ScrollPosition.offset(1));
		source.remove("b");
		assertThat(window.positionAt("b")).isEqualTo(ScrollPosition.offset(1));
	}

	@Test
	void mappedWindowIsImmutableSnapshot() {

		Window<String> window = Window.from(new ArrayList<>(List.of("a", "bb", "ccc")),
				OffsetScrollPosition.positionFunction(0));
		Window<Integer> mapped = window.map(String::length);
		List<Integer> content = mapped.getContent();

		assertThatThrownBy(() -> content.add(4)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> content.remove(0)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> content.set(0, 99)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void mappedWindowPreservesPositionFunction() {

		Window<String> window = Window.from(List.of("a", "bb", "ccc"), OffsetScrollPosition.positionFunction(0));
		Window<Integer> mapped = window.map(String::length);

		assertThat(mapped.positionAt(0)).isEqualTo(ScrollPosition.offset(0));
		assertThat(mapped.positionAt(2)).isEqualTo(ScrollPosition.offset(2));
	}

	@Test
	void mappedWindowPreservesHasNext() {

		Window<String> window = Window.from(List.of("a", "b"), OffsetScrollPosition.positionFunction(0), true);
		Window<Integer> mapped = window.map(String::length);

		assertThat(mapped.hasNext()).isTrue();
		assertThat(mapped.isLast()).isFalse();
	}
}
