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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.ScrollPosition.Direction;
import org.springframework.data.support.WindowIterator;
import org.springframework.data.util.Streamable;

/**
 * Unit tests for {@link WindowIterator}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WindowIteratorUnitTests {

	@Test // GH-2151
	void loadsDataOnNext() {

		Function<ScrollPosition, Window<String>> fkt = mock(Function.class);
		WindowIterator<String> iterator = WindowIterator.of(fkt).startingAt(ScrollPosition.offset());
		verifyNoInteractions(fkt);

		when(fkt.apply(any())).thenReturn(Window.from(Collections.emptyList(), value -> ScrollPosition.offset()));

		iterator.hasNext();
		verify(fkt).apply(ScrollPosition.offset());
	}

	@Test // GH-2151
	void hasNextReturnsFalseIfNoDataAvailable() {

		Window<Object> window = Window.from(Collections.emptyList(), value -> ScrollPosition.offset());
		WindowIterator<Object> iterator = WindowIterator.of(it -> window).startingAt(ScrollPosition.offset());

		assertThat(iterator.hasNext()).isFalse();
	}

	@Test // GH-2151
	void nextStillThrowsNoSuchElementExceptionWhenNoDataAvailable() {

		Window<Object> window = Window.from(Collections.emptyList(), value -> ScrollPosition.offset());
		WindowIterator<Object> iterator = WindowIterator.of(it -> window).startingAt(ScrollPosition.offset());

		assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
	}

	@Test // GH-2151
	void hasNextReturnsTrueIfDataAvailableButOnlyOnePage() {

		Window<String> window = Window.from(List.of("a", "b"), value -> ScrollPosition.offset());
		WindowIterator<String> iterator = WindowIterator.of(it -> window).startingAt(ScrollPosition.offset());

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("a");
		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("b");
		assertThat(iterator.hasNext()).isFalse();
		assertThat(iterator.hasNext()).isFalse();
	}

	@Test // GH-2151
	void hasNextReturnsCorrectlyIfNextPageIsEmpty() {

		Window<String> window = Window.from(List.of("a", "b"), value -> ScrollPosition.offset());
		WindowIterator<String> iterator = WindowIterator.of(it -> {
			if (it.isInitial()) {
				return window;
			}

			return Window.from(Collections.emptyList(), OffsetScrollPosition::of, false);
		}).startingAt(ScrollPosition.offset());

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("a");
		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("b");
		assertThat(iterator.hasNext()).isFalse();
		assertThat(iterator.hasNext()).isFalse();
	}

	@Test // GH-2151
	void allowsToIterateAllWindows() {

		Window<String> window1 = Window.from(List.of("a", "b"), ScrollPosition::offset, true);
		Window<String> window2 = Window.from(List.of("c", "d"), value -> ScrollPosition.offset(2 + value));
		WindowIterator<String> iterator = WindowIterator.of(it -> {
			if (it.isInitial()) {
				return window1;
			}

			return window2;
		}).startingAt(ScrollPosition.offset());

		List<String> capturedResult = new ArrayList<>(4);
		while (iterator.hasNext()) {
			capturedResult.add(iterator.next());
		}

		assertThat(capturedResult).containsExactly("a", "b", "c", "d");
	}

	@Test // GH-2151, GH-2857
	void considersBackwardKeysetScrolling() {

		Window<String> initial = Window.from(List.of("c", "d"),
				value -> KeysetScrollPosition.of(Map.of("k", 10 + value), Direction.BACKWARD), true);
		Window<String> terminal = Window.from(List.of("a", "b"),
				value -> KeysetScrollPosition.of(Map.of("k", value), Direction.BACKWARD));

		WindowIterator<String> iterator = WindowIterator.of(it -> {

			if (it instanceof KeysetScrollPosition ksp) {
				if (Integer.valueOf(10).equals(ksp.getKeys().get("k"))) {
					return terminal;
				}
			}

			return initial;

		}).startingAt(ScrollPosition.keyset().backward());

		List<String> items = Streamable.of(() -> iterator).toList();
		assertThat(items).containsExactly("d", "c", "b", "a");
	}

	@Test
	void repeatedHasNextDoesNotReloadCurrentWindow() {

		Function<ScrollPosition, Window<String>> windowFunction = mock(Function.class);
		when(windowFunction.apply(any())).thenReturn(Window.from(List.of("a", "b"), ScrollPosition::offset));

		WindowIterator<String> iterator = WindowIterator.of(windowFunction).startingAt(ScrollPosition.offset());

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.hasNext()).isTrue();

		verify(windowFunction, times(1)).apply(ScrollPosition.offset());
		verifyNoMoreInteractions(windowFunction);
	}

	@Test
	void terminalHasNextDoesNotReloadAfterExhaustion() {

		AtomicInteger invocations = new AtomicInteger();
		WindowIterator<String> iterator = WindowIterator.of(position -> {
			invocations.incrementAndGet();
			return Window.from(List.of("a"), ScrollPosition::offset);
		}).startingAt(ScrollPosition.offset());

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("a");
		assertThat(iterator.hasNext()).isFalse();
		assertThat(iterator.hasNext()).isFalse();
		assertThat(iterator.hasNext()).isFalse();
		assertThat(invocations).hasValue(1);
	}

	@Test
	void nextLoadsFollowingWindowUsingLastForwardPosition() {

		List<ScrollPosition> positions = new ArrayList<>();
		Window<String> window1 = Window.from(List.of("a", "b"), value -> ScrollPosition.offset(10 + value), true);
		Window<String> window2 = Window.from(List.of("c"), value -> ScrollPosition.offset(12 + value));
		WindowIterator<String> iterator = WindowIterator.of(position -> {
			positions.add(position);
			return positions.size() == 1 ? window1 : window2;
		}).startingAt(ScrollPosition.offset());

		assertThat(iterator.next()).isEqualTo("a");
		assertThat(iterator.next()).isEqualTo("b");
		assertThat(iterator.next()).isEqualTo("c");
		assertThat(positions).containsExactly(ScrollPosition.offset(), ScrollPosition.offset(11));
	}

	@Test
	void backwardKeysetUsesFirstPositionForNextWindow() {

		List<ScrollPosition> positions = new ArrayList<>();
		Window<String> initial = Window.from(List.of("c", "d"),
				value -> KeysetScrollPosition.of(Map.of("k", 10 + value), Direction.BACKWARD), true);
		Window<String> terminal = Window.from(List.of("a", "b"),
				value -> KeysetScrollPosition.of(Map.of("k", value), Direction.BACKWARD));
		WindowIterator<String> iterator = WindowIterator.of(position -> {
			positions.add(position);
			if (position instanceof KeysetScrollPosition ksp && Integer.valueOf(10).equals(ksp.getKeys().get("k"))) {
				return terminal;
			}
			return initial;
		}).startingAt(ScrollPosition.keyset().backward());

		assertThat(Streamable.of(() -> iterator).toList()).containsExactly("d", "c", "b", "a");
		assertThat(positions).hasSize(2);
		assertThat(positions.get(1)).isInstanceOfSatisfying(KeysetScrollPosition.class, position -> {
			assertThat(position.scrollsBackward()).isTrue();
			assertThat(position.getKeys()).containsEntry("k", 10);
		});
	}

	@Test
	void nullWindowFromFunctionFailsWithIllegalStateException() {

		WindowIterator<String> iterator = WindowIterator.<String>of(position -> null).startingAt(ScrollPosition.offset());

		assertThatIllegalStateException().isThrownBy(iterator::hasNext)
				.withMessageContaining("Window function must not return null");
	}

	@Test
	void emptyWindowWithHasNextFailsWithIllegalStateException() {

		Window<String> window = Window.from(Collections.emptyList(), OffsetScrollPosition::of, true);
		WindowIterator<String> iterator = WindowIterator.of(position -> window).startingAt(ScrollPosition.offset());

		assertThatIllegalStateException().isThrownBy(iterator::hasNext)
				.withMessageContaining("Window must not be empty when hasNext is true");
	}

	@Test
	void nonProgressingScrollPositionFailsWithIllegalStateException() {

		Window<String> window = Window.from(List.of("a"), value -> ScrollPosition.offset(5), true);
		WindowIterator<String> iterator = WindowIterator.of(position -> window).startingAt(ScrollPosition.offset(5));

		assertThat(iterator.next()).isEqualTo("a");
		assertThatIllegalStateException().isThrownBy(iterator::hasNext)
				.withMessageContaining("Window did not advance ScrollPosition");
	}

	@Test
	void positionAtFailureDoesNotAdvanceIteratorState() {

		AtomicInteger invocations = new AtomicInteger();
		Window<String> window = Window.from(List.of("a"), value -> {
			throw new IllegalStateException("boom");
		}, true);
		WindowIterator<String> iterator = WindowIterator.of(position -> {
			invocations.incrementAndGet();
			return window;
		}).startingAt(ScrollPosition.offset());

		assertThat(iterator.next()).isEqualTo("a");
		assertThatIllegalStateException().isThrownBy(iterator::hasNext).withMessage("boom");
		assertThat(invocations).hasValue(1);
		assertThatIllegalStateException().isThrownBy(iterator::hasNext).withMessage("boom");
		assertThat(invocations).hasValue(1);
	}
}
