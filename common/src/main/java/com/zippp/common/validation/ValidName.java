package com.zippp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidName.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidName {

    String message() default "name must be 1-20 characters";

    boolean required() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidName, String> {
        private boolean required;
        private static final int MAX_LENGTH = 20;

        @Override
        public void initialize(ValidName annotation) {
            this.required = annotation.required();
        }

        @Override
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return !required;
            }
            return value.length() <= MAX_LENGTH;
        }
    }
}