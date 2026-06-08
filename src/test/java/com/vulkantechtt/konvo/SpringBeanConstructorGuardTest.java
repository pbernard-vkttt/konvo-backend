package com.vulkantechtt.konvo;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class SpringBeanConstructorGuardTest {

    private static final String BASE_PACKAGE = "com.vulkantechtt.konvo";

    @Test
    void springBeansWithMultipleConstructorsDeclareOneAutowiredConstructor() throws Exception {
        List<String> violations = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        for (var beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> beanType = Class.forName(beanDefinition.getBeanClassName());
            Constructor<?>[] constructors = beanType.getDeclaredConstructors();
            if (constructors.length <= 1) {
                continue;
            }

            List<Constructor<?>> autowiredConstructors = Arrays.stream(constructors)
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .sorted(Comparator.comparing(Constructor::toGenericString))
                    .toList();
            if (autowiredConstructors.size() != 1) {
                violations.add(beanType.getName() + " has " + constructors.length
                        + " constructors and " + autowiredConstructors.size()
                        + " @Autowired constructors");
            }
        }

        assertThat(violations)
                .as("Spring beans with multiple constructors must mark exactly one constructor with @Autowired")
                .isEmpty();
    }
}
