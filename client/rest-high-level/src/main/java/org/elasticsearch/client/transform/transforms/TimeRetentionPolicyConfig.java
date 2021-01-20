package org.elasticsearch.client.transform.transforms;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.optionalConstructorArg;

public class TimeRetentionPolicyConfig implements RetentionPolicyConfig {

    public static final String NAME = "time";

    private static final ParseField FIELD = new ParseField("field");
    private static final ParseField MAX_AGE = new ParseField("max_age");

    private final String field;
    private final TimeValue maxAge;

    private static final ConstructingObjectParser<TimeRetentionPolicyConfig, Void> PARSER = new ConstructingObjectParser<>(
        "time_retention_policy_config",
        true,
        args -> new TimeRetentionPolicyConfig((String) args[0], args[1] != null ? (TimeValue) args[1] : TimeValue.ZERO)
    );

    static {
        PARSER.declareString(constructorArg(), FIELD);
        PARSER.declareField(
            constructorArg(),
            (p, c) -> TimeValue.parseTimeValue(p.text(), MAX_AGE.getPreferredName()),
            MAX_AGE,
            ObjectParser.ValueType.STRING
        );
    }

    public static TimeRetentionPolicyConfig fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    public TimeRetentionPolicyConfig(String field, TimeValue maxAge) {
        this.field = field;
        this.maxAge = maxAge;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FIELD.getPreferredName(), field);
        if (maxAge.duration() > 0) {
            builder.field(MAX_AGE.getPreferredName(), maxAge.getStringRep());
        }
        builder.endObject();
        return builder;
    }

    public String getField() {
        return field;
    }

    public TimeValue getMaxAge() {
        return maxAge;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final TimeRetentionPolicyConfig that = (TimeRetentionPolicyConfig) other;

        return Objects.equals(this.field, that.field) && Objects.equals(this.maxAge, that.maxAge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, maxAge);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
