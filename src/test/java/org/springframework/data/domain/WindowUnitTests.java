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
		assertThat(window.positionAt(1)).isEqualTo(ScrollPosition.offset(1));
		assertThat(window.positionAt(Integer.valueOf(1))).isEqualTo(ScrollPosition.offset(0));
	}

	@Test
	void windowTakesSnapshotAgainstSourceListAdd() {

		List<Integer> source = new ArrayList<>(List.of(1, 2));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.add(3);

		assertThat(window.getContent()).containsExactly(1, 2);
		assertThat(window.size()).isEqualTo(2);
	}

	@Test
	void windowTakesSnapshotAgainstSourceListRemove() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.remove(1);

		assertThat(window.getContent()).containsExactly(1, 2, 3);
		assertThat(window.size()).isEqualTo(3);
	}

	@Test
	void windowTakesSnapshotAgainstSourceListSet() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.set(1, 9);

		assertThat(window.getContent()).containsExactly(1, 2, 3);
		assertThat(window.size()).isEqualTo(3);
	}

	@Test
	void windowTakesSnapshotAgainstSourceListClear() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.clear();

		assertThat(window.getContent()).containsExactly(1, 2, 3);
		assertThat(window.size()).isEqualTo(3);
	}

	@Test
	void windowContentRejectsAddRemoveSetClear() {

		Window<Integer> window = Window.from(new ArrayList<>(List.of(1, 2, 3)), OffsetScrollPosition.positionFunction(0));
		List<Integer> content = window.getContent();

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> content.add(4));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> content.remove(0));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> content.set(0, 4));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(content::clear);
	}

	@Test
	void windowIteratorRejectsRemove() {

		Window<Integer> window = Window.from(List.of(1, 2, 3), OffsetScrollPosition.positionFunction(0));
		Iterator<Integer> iterator = window.iterator();

		assertThat(iterator.next()).isEqualTo(1);
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove);
	}

	@Test
	void positionAtUsesConstructionSnapshot() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.clear();

		assertThat(window.positionAt(2)).isEqualTo(ScrollPosition.offset(2));
	}

	@Test
	void positionAtObjectUsesConstructionSnapshot() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<Integer> window = Window.from(source, OffsetScrollPosition.positionFunction(0));

		source.set(1, 9);

		assertThat(window.positionAt(Integer.valueOf(2))).isEqualTo(ScrollPosition.offset(1));
	}

	@Test
	void mappedWindowIsImmutableSnapshot() {

		List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
		Window<String> mapped = Window.from(source, OffsetScrollPosition.positionFunction(0), true).map(Object::toString);

		source.clear();

		assertThat(mapped.getContent()).containsExactly("1", "2", "3");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> mapped.getContent().add("4"));
	}

	@Test
	void mappedWindowPreservesPositionFunction() {

		Window<String> mapped = Window.from(List.of(1, 2, 3), OffsetScrollPosition.positionFunction(5), true)
				.map(Object::toString);

		assertThat(mapped.positionAt(1)).isEqualTo(ScrollPosition.offset(6));
	}

	@Test
	void mappedWindowPreservesHasNext() {

		Window<String> mapped = Window.from(List.of(1, 2, 3), OffsetScrollPosition.positionFunction(0), true)
				.map(Object::toString);

		assertThat(mapped.hasNext()).isTrue();
		assertThat(mapped.isLast()).isFalse();
	}
}
