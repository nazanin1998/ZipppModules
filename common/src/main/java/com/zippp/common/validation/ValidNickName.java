package com.zippp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;

import java.lang.annotation.*;


@Documented
@Constraint(validatedBy = ValidNickName.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNickName {

    String message() default "nickName must be 3-50 characters, start with a letter or digit, "
            + "and contain only letters, digits, dots, underscores, or dashes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidNickName, String> {
        private static final java.util.regex.Pattern PATTERN =
                java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,49}$");

        @Override
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return false;
            }
            return PATTERN.matcher(value).matches();
        }
    }
}