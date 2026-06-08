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
package org.springframework.data.web;

import static org.assertj.core.api.Assertions.*;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.StringUtils;

/**
 * Unit tests for {@link ReactiveOffsetScrollPositionHandlerMethodArgumentResolver}.
 *
 * @author Yanming Zhou
 * @author Mark Paluch
 */
class ReactiveOffsetScrollPositionHandlerMethodArgumentResolverUnitTests {

	static final MethodParameter PARAMETER = getParameterOfMethod("supportedMethod");
	static final MethodParameter OPTIONAL_PARAMETER = getOptionalParameterOfMethod("supportedMethodWithOptional");

	final ReactiveOffsetScrollPositionHandlerMethodArgumentResolver sut = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();

	@Test // GH-2856
	void supportsOffsetScrollPositionParameter() {
		assertThat(sut.supportsParameter(PARAMETER)).isTrue();
	}

	@Test // GH-2856
	void supportsOptionalOffsetScrollPositionParameter() {
		assertThat(sut.supportsParameter(OPTIONAL_PARAMETER)).isTrue();
	}

	@Test // GH-2856
	void fallbackToNullOffset() {

		var parameter = TestUtils.getParameterOfMethod(Controller.class, "unsupportedMethod", String.class);
		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(sut, request, parameter);

		assertThat(position).isNull();
	}

	@Test // GH-2856
	void discoversOffsetFromRequest() {

		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(sut, getRequestWithOffset(reference, null, null), PARAMETER, reference);
	}

	@Test
	void discoversOffsetFromRequestWithPrefix() {

		var reference = ScrollPosition.offset(5);
		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", null), PARAMETER, reference);
	}

	@Test // GH-2856
	void returnsNullIfNotSpecified() {

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(sut, request, PARAMETER);

		assertThat(position).isNull();
	}

	@Test // GH-2856
	void returnsOptionalParameterFromRequest() {

		var request = MockServerHttpRequest.get("/foo?offset=5").build();
		var position = resolveOffset(sut, request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.of(ScrollPosition.offset(5)));
	}

	@Test
	void returnsOptionalParameterFromRequestWithPrefix() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		var request = MockServerHttpRequest.get("/foo?p_offset=5").build();
		var position = resolveOffset(resolver, request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.of(ScrollPosition.offset(5)));
	}

	@Test // GH-2856
	void returnsEmptyOptionalIfNotSpecified() {

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(sut, request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.empty());
	}

	@Test
	void returnsEmptyOptionalForEmptyOffsetParameter() {

		var request = MockServerHttpRequest.get("/foo?offset=").build();
		var position = resolveOffset(sut, request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.empty());
	}

	@Test
	void returnsEmptyOptionalForInvalidOffsetParameter() {

		var request = MockServerHttpRequest.get("/foo?offset=invalid_number").build();
		var position = resolveOffset(sut, request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.empty());
	}

	@Test // GH-2856
	void discoversOffsetFromRequestWithMultipleParams() {

		var request = MockServerHttpRequest.get("/foo?offset=5&offset=6").build();

		assertThat(resolveOffset(sut, request, PARAMETER)).isEqualTo(ScrollPosition.offset(5));
	}

	@Test
	void discoversOffsetFromRequestWithPrefixedMultipleParams() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		var request = MockServerHttpRequest.get("/foo?p_offset=5&p_offset=6").build();

		assertThat(resolveOffset(resolver, request, PARAMETER)).isEqualTo(ScrollPosition.offset(5));
	}

	@Test // GH-2856
	void discoversQualifiedOffsetFromRequest() {

		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(sut, getRequestWithOffset(reference, null, "hello"), parameter, reference);
	}

	@Test
	void discoversQualifiedOffsetFromRequestWithPrefix() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", "hello"), parameter, reference);
	}

	@Test
	void discoversQualifiedOffsetFromRequestWithPrefixAndCustomParameterName() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		resolver.setOffsetParameter("cursor");
		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", "hello", "cursor", "_"), parameter,
				reference);
	}

	@Test
	void discoversQualifiedOffsetFromRequestWithPrefixAndCustomQualifierDelimiter() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		resolver.setQualifierDelimiter(".");
		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", "hello", "offset", "."), parameter,
				reference);
	}

	@Test // GH-2856
	void returnsNullForEmptyOffsetParam() {

		var request = MockServerHttpRequest.get("/foo?offset=").build();

		assertThat(resolveOffset(sut, request, PARAMETER)).isNull();
	}

	@Test // GH-2856
	void returnsNullForOffsetParamIsInvalidProperty() {

		var request = MockServerHttpRequest.get("/foo?offset=invalid_number").build();

		assertThat(resolveOffset(sut, request, PARAMETER)).isNull();
	}

	@Test // GH-2856
	void emptyQualifierIsUsedInParameterLookup() {

		var parameter = getParameterOfMethod("emptyQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(sut, getRequestWithOffset(reference, null, ""), parameter, reference);
	}

	@Test
	void emptyQualifierWithPrefixIsUsedInParameterLookup() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		var parameter = getParameterOfMethod("emptyQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", ""), parameter, reference);
	}

	@Test // GH-2856
	void mergedQualifierIsUsedInParameterLookup() {

		var parameter = getParameterOfMethod("mergedQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(sut, getRequestWithOffset(reference, null, "merged"), parameter, reference);
	}

	@Test
	void mergedQualifierWithPrefixIsUsedInParameterLookup() {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		resolver.setPrefix("p_");
		var parameter = getParameterOfMethod("mergedQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(resolver, getRequestWithOffset(reference, "p_", "merged"), parameter, reference);
	}

	@Nullable
	private static Object resolveOffset(ReactiveOffsetScrollPositionHandlerMethodArgumentResolver resolver,
			MockServerHttpRequest request, MethodParameter parameter) {
		return resolver.resolveArgumentValue(parameter, null, MockServerWebExchange.from(request));
	}

	private void assertSupportedAndResolvedTo(ReactiveOffsetScrollPositionHandlerMethodArgumentResolver resolver,
			MockServerHttpRequest request, MethodParameter parameter, OffsetScrollPosition position) {

		assertThat(resolver.supportsParameter(parameter)).isTrue();

		var resolved = resolveOffset(resolver, request, parameter);
		assertThat(resolved).isEqualTo(position);
	}

	private static MethodParameter getParameterOfMethod(String name) {
		return TestUtils.getParameterOfMethod(Controller.class, name, OffsetScrollPosition.class);
	}

	private static MethodParameter getOptionalParameterOfMethod(String name) {
		return TestUtils.getParameterOfMethod(Controller.class, name, Optional.class);
	}

	private static MockServerHttpRequest getRequestWithOffset(@Nullable OffsetScrollPosition position,
			@Nullable String prefix, @Nullable String qualifier) {
		return getRequestWithOffset(position, prefix, qualifier, "offset", "_");
	}

	private static MockServerHttpRequest getRequestWithOffset(@Nullable OffsetScrollPosition position,
			@Nullable String prefix, @Nullable String qualifier, String offsetParameter, String qualifierDelimiter) {

		if (position == null) {
			return TestUtils.getWebfluxRequest();
		}

		StringBuilder parameterName = new StringBuilder(prefix == null ? "" : prefix);

		if (StringUtils.hasLength(qualifier)) {
			parameterName.append(qualifier).append(qualifierDelimiter);
		}

		parameterName.append(offsetParameter);
		return MockServerHttpRequest
				.get(String.format("foo?%s=%s", parameterName, String.valueOf(position.getOffset()))).build();
	}

	interface Controller {

		void supportedMethod(OffsetScrollPosition offset);

		void supportedMethodWithOptional(Optional<OffsetScrollPosition> offset);

		void unsupportedMethod(String string);

		void qualifiedOffset(@Qualifier("hello") OffsetScrollPosition offset);

		void emptyQualifier(@Qualifier OffsetScrollPosition offset);

		void mergedQualifier(@TestQualifier OffsetScrollPosition offset);
	}
}
