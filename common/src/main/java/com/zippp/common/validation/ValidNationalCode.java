package com.zippp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidNationalCode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNationalCode {

    String message() default "nationalCode must be empty or exactly 10 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidNationalCode, String> {
        private static final java.util.regex.Pattern PATTERN =
                java.util.regex.Pattern.compile("^[0-9]{10}$");

        @Override
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            return PATTERN.matcher(value).matches();
        }
    }
}