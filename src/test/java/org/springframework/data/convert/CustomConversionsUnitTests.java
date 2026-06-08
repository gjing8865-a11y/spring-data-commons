/*
 * Copyright 2011-present the original author or authors.
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
package org.springframework.data.convert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.jmolecules.ddd.types.Association;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.convert.CustomConversions.ConverterConfiguration;
import org.springframework.data.convert.CustomConversions.StoreConversions;
import org.springframework.data.convert.Jsr310Converters.LocalDateTimeToDateConverter;
import org.springframework.data.geo.Point;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link CustomConversions}.
 *
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 2.0
 */
class CustomConversionsUnitTests {

	static final SimpleTypeHolder DATE_EXCLUDING_SIMPLE_TYPE_HOLDER = new SimpleTypeHolder(
			Collections.singleton(Date.class), true) {

		@Override
		public boolean isSimpleType(Class<?> type) {
			return !type.getName().startsWith("java.time") && super.isSimpleType(type);
		}
	};

	@Test // DATACMNS-1035
	void findsBasicReadAndWriteConversions() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Arrays.asList(FormatToStringConverter.INSTANCE, StringToFormatConverter.INSTANCE));

		assertThat(conversions.getCustomWriteTarget(Format.class)).hasValue(String.class);
		assertThat(conversions.getCustomWriteTarget(String.class)).isNotPresent();

		assertThat(conversions.hasCustomReadTarget(String.class, Format.class)).isTrue();
		assertThat(conversions.hasCustomReadTarget(String.class, Locale.class)).isFalse();
	}

	@Test // DATACMNS-1035
	void considersSubtypesCorrectly() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Arrays.asList(NumberToStringConverter.INSTANCE, StringToNumberConverter.INSTANCE));

		assertThat(conversions.getCustomWriteTarget(Long.class)).hasValue(String.class);
		assertThat(conversions.hasCustomReadTarget(String.class, Long.class)).isTrue();
	}

	@Test // DATACMNS-1101
	void considersSubtypeCachingCorrectly() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Arrays.asList(NumberToStringConverter.INSTANCE, StringToNumberConverter.INSTANCE));

		assertThat(conversions.getCustomWriteTarget(Long.class, Object.class)).isEmpty();
		assertThat(conversions.getCustomWriteTarget(Long.class)).hasValue(String.class);
		assertThat(conversions.getCustomWriteTarget(Long.class, Object.class)).isEmpty();
	}

	@Test // DATACMNS-1035
	void populatesConversionServiceCorrectly() {

		GenericConversionService conversionService = new DefaultConversionService();

		var conversions = new CustomConversions(StoreConversions.NONE,
				Collections.singletonList(StringToFormatConverter.INSTANCE));
		conversions.registerConvertersIn(conversionService);

		assertThat(conversionService.canConvert(String.class, Format.class)).isTrue();
	}

	@Test // DATAMONGO-259, DATACMNS-1035
	void doesNotConsiderTypeSimpleIfOnlyReadConverterIsRegistered() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Collections.singletonList(StringToFormatConverter.INSTANCE));
		assertThat(conversions.isSimpleType(Format.class)).isFalse();
	}

	@Test // DATAMONGO-298, DATACMNS-1035
	void discoversConvertersForSubtypesOfMongoTypes() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Collections.singletonList(StringToIntegerConverter.INSTANCE));
		assertThat(conversions.hasCustomReadTarget(String.class, Integer.class)).isTrue();
		assertThat(conversions.hasCustomWriteTarget(String.class, Integer.class)).isTrue();
	}

	@Test // DATAMONGO-1001, DATACMNS-1035
	void shouldSelectPropertCustomWriteTargetForCglibProxiedType() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Collections.singletonList(FormatToStringConverter.INSTANCE));
		assertThat(conversions.getCustomWriteTarget(createProxyTypeFor(Format.class))).hasValue(String.class);
	}

	@Test // DATAMONGO-1001, DATACMNS-1035
	void shouldSelectPropertCustomReadTargetForCglibProxiedType() {

		var conversions = new CustomConversions(StoreConversions.NONE,
				Collections.singletonList(CustomTypeToStringConverter.INSTANCE));
		assertThat(conversions.hasCustomReadTarget(createProxyTypeFor(CustomType.class), String.class)).isTrue();
	}

	@Test // DATAMONGO-1131, DATACMNS-1035
	void registersConvertersForJsr310() {

		var customConversions = new CustomConversions(StoreConversions.NONE, Collections.emptyList());

		assertThat(customConversions.hasCustomWriteTarget(java.time.LocalDateTime.class)).isTrue();
	}

	@Test // DATAMONGO-1302, DATACMNS-1035
	void registersConverterFactoryCorrectly() {

		var conversions = StoreConversions.of(new SimpleTypeHolder(Collections.singleton(Format.class), true));

		var customConversions = new CustomConversions(conversions, Collections.singletonList(new FormatConverterFactory()));

		assertThat(customConversions.getCustomWriteTarget(String.class, SimpleDateFormat.class)).isPresent();
	}

	@Test // DATACMNS-1034
	void registersConverterFromConverterAware() {

		var converters = ConverterBuilder //
				.reading(Locale.class, CustomType.class, left -> new CustomType()) //
				.andWriting(right -> Locale.GERMAN);

		var conversions = new CustomConversions(StoreConversions.NONE, Collections.singletonList(converters));

		assertThat(conversions.hasCustomWriteTarget(CustomType.class)).isTrue();
		assertThat(conversions.hasCustomReadTarget(Locale.class, CustomType.class)).isTrue();

		ConfigurableConversionService conversionService = new GenericConversionService();
		conversions.registerConvertersIn(conversionService);

		assertThat(conversionService.canConvert(CustomType.class, Locale.class)).isTrue();
		assertThat(conversionService.canConvert(Locale.class, CustomType.class)).isTrue();
	}

	@Test // DATACMNS-1615
	void skipsUnsupportedDefaultWritingConverter() {

		var registry = mock(ConverterRegistry.class);

		new CustomConversions(StoreConversions.of(DATE_EXCLUDING_SIMPLE_TYPE_HOLDER), Collections.emptyList())
				.registerConvertersIn(registry);

		verify(registry, never()).addConverter(any(Jsr310Converters.LocalDateTimeToInstantConverter.class));
	}

	@Test // DATACMNS-1665
	void registersStoreConverter() {

		var registry = mock(ConverterRegistry.class);

		var holder = new SimpleTypeHolder(Collections.emptySet(), true);

		var conversions = new CustomConversions(StoreConversions.of(holder, PointToMapConverter.INSTANCE),
				Collections.emptyList());
		conversions.registerConvertersIn(registry);

		assertThat(conversions.isSimpleType(Point.class)).isTrue(); // Point is a custom simple type
		verify(registry).addConverter(any(PointToMapConverter.class));
	}

	@Test // DATACMNS-1615
	void doesNotSkipUnsupportedUserConverter() {

		var registry = mock(ConverterRegistry.class);

		new CustomConversions(StoreConversions.of(DATE_EXCLUDING_SIMPLE_TYPE_HOLDER),
				Collections.singletonList(Jsr310Converters.LocalDateTimeToInstantConverter.INSTANCE))
				.registerConvertersIn(registry);

		verify(registry).addConverter(any(Jsr310Converters.LocalDateTimeToInstantConverter.class));
	}

	@Test // DATACMNS-1615
	void skipsConverterBasedOnConfiguration() {

		var registry = mock(ConverterRegistry.class);

		var config = new ConverterConfiguration(StoreConversions.NONE, Collections.emptyList(),
				Predicate.<ConvertiblePair> isEqual(new ConvertiblePair(java.time.LocalDateTime.class, Date.class)).negate());
		new CustomConversions(config).registerConvertersIn(registry);

		verify(registry, never()).addConverter(any(LocalDateTimeToDateConverter.class));
	}

	@Test // DATACMNS-1615
	void doesNotSkipUserConverterConverterEvenWhenConfigurationWouldNotAllowIt() {

		var registry = mock(ConverterRegistry.class);

		var config = new ConverterConfiguration(StoreConversions.NONE,
				Collections.singletonList(LocalDateTimeToDateConverter.INSTANCE),
				Predicate.<ConvertiblePair> isEqual(new ConvertiblePair(java.time.LocalDateTime.class, Date.class)).negate());
		new CustomConversions(config).registerConvertersIn(registry);

		verify(registry).addConverter(any(LocalDateTimeToDateConverter.class));
	}

	@Test // GH-2315
	void addsAssociationConvertersByDefault() {

		var conversions = new CustomConversions(StoreConversions.NONE, Collections.emptyList());

		assertThat(conversions.hasCustomWriteTarget(Association.class)).isTrue();
		assertThat(conversions.hasCustomReadTarget(Object.class, Association.class)).isTrue();
	}

	@Test // GH-2315
	void addsIdentifierConvertersByDefault() {

		var conversions = new CustomConversions(StoreConversions.NONE, Collections.emptyList());

		assertThat(conversions.hasCustomWriteTarget(Identifier.class)).isTrue();
		assertThat(conversions.hasCustomReadTarget(String.class, Identifier.class)).isTrue();
	}

	@Test // GH-2511
	void registersVavrConverters() {

		ConfigurableConversionService conversionService = new DefaultConversionService();

		new CustomConversions(StoreConversions.NONE, Collections.emptyList()).registerConvertersIn(conversionService);

		assertThat(conversionService.canConvert(io.vavr.collection.List.class, List.class)).isTrue();
		assertThat(conversionService.canConvert(List.class, io.vavr.collection.List.class)).isTrue();
	}

	@Test // GH-3304
	void registersMultipleConvertersCorrectly() {

		CustomConversions customConversions = new CustomConversions(new ConverterConfiguration(StoreConversions.NONE,
				List.of(PointToMapConverter.INSTANCE, new FormatConverterFactory()), (it) -> true, null));

		assertThat(customConversions.hasCustomWriteTarget(Point.class, Map.class)).isTrue();
		assertThat(customConversions.hasCustomWriteTarget(String.class, Format.class)).isTrue();
	}

	@Test // GH-1484
	void doesNotFailIfPropertiesConversionIsNull() {

		new CustomConversions(
				new ConverterConfiguration(StoreConversions.NONE, Collections.emptyList(), (it) -> true, null));
	}

	@Test
	void hasValueConverterReturnsFalseWhenNoPropertyValueConversionsAreConfigured() {

		ConverterConfiguration configuration = new ConverterConfiguration(StoreConversions.NONE, Collections.emptyList(),
				it -> true, null);

		CustomConversions conversions = new CustomConversions(configuration);

		PersistentProperty<?> mockProperty = mock(PersistentProperty.class);

		assertThat(conversions.getPropertyValueConversions()).isNull();
		assertThat(conversions.hasValueConverter(mockProperty)).isFalse();

		verifyNoInteractions(mockProperty);
	}

	@Test
	public void hasValueConverterReturnsTrueWhenConverterRegisteredForProperty() {

		PersistentProperty<?> mockProperty = mock(PersistentProperty.class);

		PropertyValueConversions mockPropertyValueConversions = mock(PropertyValueConversions.class);

		doReturn(true).when(mockPropertyValueConversions).hasValueConverter(eq(mockProperty));

		ConverterConfiguration configuration = new ConverterConfiguration(StoreConversions.NONE, Collections.emptyList(),
				it -> true, mockPropertyValueConversions);

		CustomConversions conversions = new CustomConversions(configuration);

		assertThat(conversions.getPropertyValueConversions()).isSameAs(mockPropertyValueConversions);
		assertThat(conversions.hasValueConverter(mockProperty)).isTrue();

		verify(mockPropertyValueConversions, times(1)).hasValueConverter(eq(mockProperty));
		verifyNoMoreInteractions(mockPropertyValueConversions);
		verifyNoInteractions(mockProperty);
	}

	@Test // GH-3306
	void doesNotWarnForAsymmetricListConverter() {

		Log actualLogger = (Log) ReflectionTestUtils.getField(CustomConversions.class, "logger");
		Log actualLoggerSpy = spy(actualLogger);
		ReflectionTestUtils.setField(CustomConversions.class, "logger", actualLoggerSpy, Log.class);

		new CustomConversions(StoreConversions.NONE, List.of(ListOfNumberToStringConverter.INSTANCE));

		verify(actualLoggerSpy, never()).warn(anyString());
		verify(actualLoggerSpy, never()).warn(anyString(), any());
	}

	// ================== 新增的测试场景 ==================

	@Test // 1. user converter 不受 default converter filter 影响
	void userConverterBypassesDefaultConverterFilter() {
		// 创建一个会过滤掉 LocalDateTimeToDateConverter 的配置
		Predicate<ConvertiblePair> filter = pair ->
				!(pair.getSourceType().equals(java.time.LocalDateTime.class)
						&& pair.getTargetType().equals(Date.class));

		ConverterConfiguration config = new ConverterConfiguration(
				StoreConversions.NONE,
				Collections.singletonList(LocalDateTimeToDateConverter.INSTANCE),
				filter);

		CustomConversions conversions = new CustomConversions(config);

		// 用户转换器应该仍然被注册，不受 filter 影响
		assertThat(conversions.hasCustomWriteTarget(java.time.LocalDateTime.class, Date.class)).isTrue();
		assertThat(conversions.getCustomWriteTarget(java.time.LocalDateTime.class)).isPresent();
	}

	@Test // 2. default converter 被 filter 跳过
	void defaultConverterIsSkippedByFilter() {
		Predicate<ConvertiblePair> filter = pair ->
				!(pair.getSourceType().equals(java.time.LocalDateTime.class)
						&& pair.getTargetType().equals(Date.class));

		ConverterConfiguration configWithFilter = new ConverterConfiguration(
				StoreConversions.NONE,
				Collections.emptyList(),
				filter);

		ConverterConfiguration configWithoutFilter = new ConverterConfiguration(
				StoreConversions.NONE,
				Collections.emptyList());

		CustomConversions conversionsWithFilter = new CustomConversions(configWithFilter);
		CustomConversions conversionsWithoutFilter = new CustomConversions(configWithoutFilter);

		// 有 filter 的配置应该不包含该默认转换器
		assertThat(conversionsWithFilter.hasCustomWriteTarget(java.time.LocalDateTime.class, Date.class)).isFalse();

		// 没有 filter 的配置应该包含该默认转换器
		assertThat(conversionsWithoutFilter.hasCustomWriteTarget(java.time.LocalDateTime.class, Date.class)).isTrue();
	}

	@Test // 3. store converter 仍按 store simple type 规则注册
	void storeConverterRegisteredByStoreSimpleTypeRules() {
		SimpleTypeHolder customSimpleTypes = new SimpleTypeHolder(
				Collections.singleton(Format.class), true);

		StoreConversions storeConversions = StoreConversions.of(
				customSimpleTypes,
				FormatToStringConverter.INSTANCE);

		CustomConversions conversions = new CustomConversions(
				storeConversions, Collections.emptyList());

		// Store converter 应该被正确注册
		assertThat(conversions.getCustomWriteTarget(Format.class)).isPresent();
		assertThat(conversions.getCustomWriteTarget(Format.class)).hasValue(String.class);
	}

	@Test // 4. subtype target cache 不污染 requested target cache
	void subtypeTargetCacheDoesNotPolluteRequestedTargetCache() {
		CustomConversions conversions = new CustomConversions(
				StoreConversions.NONE,
				Collections.singletonList(NumberToStringConverter.INSTANCE));

		// 首先查询 Long → Object（应该返回空）
		assertThat(conversions.getCustomWriteTarget(Long.class, Object.class)).isEmpty();

		// 然后查询 Long（应该返回 String）
		assertThat(conversions.getCustomWriteTarget(Long.class)).hasValue(String.class);

		// 再次查询 Long → Object（仍然应该返回空，不受第二个查询影响）
		assertThat(conversions.getCustomWriteTarget(Long.class, Object.class)).isEmpty();
	}

	@Test // 5. absent target 缓存后，不影响同 source 的另一个 requested target 查询
	void absentTargetCacheDoesNotAffectOtherRequestedTargets() {
		CustomConversions conversions = new CustomConversions(
				StoreConversions.NONE,
				Arrays.asList(
						FormatToStringConverter.INSTANCE,
						StringToFormatConverter.INSTANCE));

		// 首先查询 Format → Integer（应该返回空，然后被缓存）
		assertThat(conversions.getCustomWriteTarget(Format.class, Integer.class)).isEmpty();

		// 然后查询 Format → String（应该正常返回）
		assertThat(conversions.getCustomWriteTarget(Format.class, String.class)).hasValue(String.class);

		// 再次查询 Format → Integer（仍然返回空）
		assertThat(conversions.getCustomWriteTarget(Format.class, Integer.class)).isEmpty();
	}

	@Test // 6. CGLIB proxy 类型仍能命中 custom write target
	void cglibProxyTypeStillHitsCustomWriteTarget() {
		CustomConversions conversions = new CustomConversions(
				StoreConversions.NONE,
				Collections.singletonList(FormatToStringConverter.INSTANCE));

		Class<?> proxyType = createProxyTypeFor(Format.class);

		assertThat(conversions.getCustomWriteTarget(proxyType)).hasValue(String.class);
	}

	@Test // 7. CGLIB proxy 类型仍能命中 custom read target
	void cglibProxyTypeStillHitsCustomReadTarget() {
		CustomConversions conversions = new CustomConversions(
				StoreConversions.NONE,
				Collections.singletonList(CustomTypeToStringConverter.INSTANCE));

		Class<?> proxyType = createProxyTypeFor(CustomType.class);

		assertThat(conversions.hasCustomReadTarget(proxyType, String.class)).isTrue();
	}

	@Test // 8. property value conversions 为 null 时行为不变
	void propertyValueConversionsNullBehaviorUnchanged() {
		ConverterConfiguration config = new ConverterConfiguration(
				StoreConversions.NONE,
				Collections.emptyList(),
				Predicate.isTrue(),
				null);

		// 创建实例应该不抛出异常
		CustomConversions conversions = new CustomConversions(config);

		// propertyValueConversions 应该返回 null
		assertThat(conversions.getPropertyValueConversions()).isNull();

		// hasValueConverter 应该返回 false
		assertThat(conversions.hasValueConverter(mock(PersistentProperty.class))).isFalse();
	}

	@Test // 9. getCustomWriteTarget(source) 与 getCustomWriteTarget(source, requestedTarget) 的缓存互不污染
	void rawAndRequestedWriteTargetCachesAreSeparate() {
		CustomConversions conversions = new CustomConversions(
				StoreConversions.NONE,
				Arrays.asList(
						NumberToStringConverter.INSTANCE,
						FormatToStringConverter.INSTANCE));

		// 先查询 Number（不带 requested target）
		assertThat(conversions.getCustomWriteTarget(Number.class)).hasValue(String.class);

		// 再查询 Number → Integer（应该返回空）
		assertThat(conversions.getCustomWriteTarget(Number.class, Integer.class)).isEmpty();

		// 再次查询 Number（不带 requested target），应该仍然返回 String
		assertThat(conversions.getCustomWriteTarget(Number.class)).hasValue(String.class);

		// 同样地，先查询 Format → String
		assertThat(conversions.getCustomWriteTarget(Format.class, String.class)).hasValue(String.class);

		// 再查询 Format（不带 requested target）
		assertThat(conversions.getCustomWriteTarget(Format.class)).hasValue(String.class);

		// 再次查询 Format → String，应该仍然返回 String
		assertThat(conversions.getCustomWriteTarget(Format.class, String.class)).hasValue(String.class);
	}

	// ================== 原有方法 ==================

	private static Class<?> createProxyTypeFor(Class<?> type) {

		var factory = new ProxyFactory();
		factory.setProxyTargetClass(true);
		factory.setTargetClass(type);

		return factory.getProxy().getClass();
	}

	@ReadingConverter
	enum ListOfNumberToStringConverter implements Converter<List<Number>, String> {

		INSTANCE;

		public String convert(List<Number> source) {
			return source.toString();
		}
	}

	enum FormatToStringConverter implements Converter<Format, String> {

		INSTANCE;

		public String convert(Format source) {
			return source.toString();
		}
	}

	enum StringToFormatConverter implements Converter<String, Format> {

		INSTANCE;

		public Format convert(String source) {
			return new DateFormat();
		}
	}

	enum NumberToStringConverter implements Converter<Number, String> {

		INSTANCE;

		public String convert(Number source) {
			return source.toString();
		}
	}

	enum StringToNumberConverter implements Converter<String, Number> {

		INSTANCE;

		public Number convert(String source) {
			return 0L;
		}
	}

	enum StringToIntegerConverter implements Converter<String, Integer> {

		INSTANCE;

		public Integer convert(String source) {
			return 0;
		}
	}

	@ReadingConverter
	enum CustomTypeToStringConverter implements Converter<CustomType, String> {

		INSTANCE;

		@Override
		public String convert(CustomType source) {
			return source != null ? source.toString() : null;
		}

	}

	@WritingConverter
	enum PointToMapConverter implements Converter<Point, Map<String, String>> {

		INSTANCE;

		@Override
		public Map<String, String> convert(Point source) {
			return source != null ? Collections.emptyMap() : null;
		}

	}

	@WritingConverter
	static class FormatConverterFactory implements ConverterFactory<String, Format> {

		@Override
		public <T extends Format> Converter<String, T> getConverter(Class<T> targetType) {
			return new StringToFormat<T>(targetType);
		}

		private static final class StringToFormat<T extends Format> implements Converter<String, T> {

			private final Class<T> targetType;

			StringToFormat(Class<T> targetType) {
				this.targetType = targetType;
			}

			@Override
			public T convert(String source) {

				if (source.length() == 0) {
					return null;
				}

				try {
					return targetType.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					throw new IllegalArgumentException(e.getMessage(), e);
				}
			}
		}
	}

	static class CustomType {}

	static class Format {}

	static class DateFormat extends Format {}

	static class SimpleDateFormat extends DateFormat {}

}
