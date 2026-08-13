package com.zippp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidPhone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "phone must be 10-15 digits, optionally prefixed with +";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhone, String> {
        private static final java.util.regex.Pattern PATTERN =
                java.util.regex.Pattern.compile("^\\+?[0-9]{10,15}$");

        @Override
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return false;
            }
            return PATTERN.matcher(value).matches();
        }
    }
}