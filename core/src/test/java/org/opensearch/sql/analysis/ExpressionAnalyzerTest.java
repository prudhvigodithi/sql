/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.opensearch.sql.analysis;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opensearch.sql.ast.dsl.AstDSL.field;
import static org.opensearch.sql.ast.dsl.AstDSL.floatLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.function;
import static org.opensearch.sql.ast.dsl.AstDSL.intLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.qualifiedName;
import static org.opensearch.sql.ast.dsl.AstDSL.stringLiteral;
import static org.opensearch.sql.ast.dsl.AstDSL.unresolvedArg;
import static org.opensearch.sql.data.model.ExprValueUtils.LITERAL_TRUE;
import static org.opensearch.sql.data.model.ExprValueUtils.integerValue;
import static org.opensearch.sql.data.type.ExprCoreType.BOOLEAN;
import static org.opensearch.sql.data.type.ExprCoreType.INTEGER;
import static org.opensearch.sql.data.type.ExprCoreType.STRING;
import static org.opensearch.sql.data.type.ExprCoreType.STRUCT;
import static org.opensearch.sql.expression.DSL.ref;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.sql.analysis.symbol.Namespace;
import org.opensearch.sql.analysis.symbol.Symbol;
import org.opensearch.sql.ast.dsl.AstDSL;
import org.opensearch.sql.ast.expression.AllFields;
import org.opensearch.sql.ast.expression.DataType;
import org.opensearch.sql.ast.expression.RelevanceFieldList;
import org.opensearch.sql.ast.expression.SpanUnit;
import org.opensearch.sql.ast.expression.UnresolvedExpression;
import org.opensearch.sql.ast.tree.UnresolvedPlan;
import org.opensearch.sql.common.antlr.SyntaxCheckException;
import org.opensearch.sql.data.model.ExprTupleValue;
import org.opensearch.sql.data.model.ExprValueUtils;
import org.opensearch.sql.exception.SemanticCheckException;
import org.opensearch.sql.expression.DSL;
import org.opensearch.sql.expression.Expression;
import org.opensearch.sql.expression.config.ExpressionConfig;
import org.opensearch.sql.expression.window.aggregation.AggregateWindowFunction;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Configuration
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ExpressionConfig.class, AnalyzerTestBase.class})
class ExpressionAnalyzerTest extends AnalyzerTestBase {

  @Test
  public void equal() {
    assertAnalyzeEqual(
        dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(integerValue(1))),
        AstDSL.equalTo(AstDSL.unresolvedAttr("integer_value"), AstDSL.intLiteral(1))
    );
  }

  @Test
  public void and() {
    assertAnalyzeEqual(
        dsl.and(DSL.ref("boolean_value", BOOLEAN), DSL.literal(LITERAL_TRUE)),
        AstDSL.and(AstDSL.unresolvedAttr("boolean_value"), AstDSL.booleanLiteral(true))
    );
  }

  @Test
  public void or() {
    assertAnalyzeEqual(
        dsl.or(DSL.ref("boolean_value", BOOLEAN), DSL.literal(LITERAL_TRUE)),
        AstDSL.or(AstDSL.unresolvedAttr("boolean_value"), AstDSL.booleanLiteral(true))
    );
  }

  @Test
  public void xor() {
    assertAnalyzeEqual(
        dsl.xor(DSL.ref("boolean_value", BOOLEAN), DSL.literal(LITERAL_TRUE)),
        AstDSL.xor(AstDSL.unresolvedAttr("boolean_value"), AstDSL.booleanLiteral(true))
    );
  }

  @Test
  public void not() {
    assertAnalyzeEqual(
        dsl.not(DSL.ref("boolean_value", BOOLEAN)),
        AstDSL.not(AstDSL.unresolvedAttr("boolean_value"))
    );
  }

  @Test
  public void qualified_name() {
    assertAnalyzeEqual(
        DSL.ref("integer_value", INTEGER),
        qualifiedName("integer_value")
    );
  }

  @Test
  public void case_value() {
    assertAnalyzeEqual(
        DSL.cases(
            DSL.literal("Default value"),
            DSL.when(
                dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(30)),
                DSL.literal("Thirty")),
            DSL.when(
                dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(50)),
                DSL.literal("Fifty"))),
        AstDSL.caseWhen(
            qualifiedName("integer_value"),
            AstDSL.stringLiteral("Default value"),
            AstDSL.when(AstDSL.intLiteral(30), AstDSL.stringLiteral("Thirty")),
            AstDSL.when(AstDSL.intLiteral(50), AstDSL.stringLiteral("Fifty"))));
  }

  @Test
  public void case_conditions() {
    assertAnalyzeEqual(
        DSL.cases(
            null,
            DSL.when(
                dsl.greater(DSL.ref("integer_value", INTEGER), DSL.literal(50)),
                DSL.literal("Fifty")),
            DSL.when(
                dsl.greater(DSL.ref("integer_value", INTEGER), DSL.literal(30)),
                DSL.literal("Thirty"))),
        AstDSL.caseWhen(
            null,
            AstDSL.when(
                AstDSL.function(">",
                    qualifiedName("integer_value"),
                    AstDSL.intLiteral(50)), AstDSL.stringLiteral("Fifty")),
            AstDSL.when(
                AstDSL.function(">",
                    qualifiedName("integer_value"),
                    AstDSL.intLiteral(30)), AstDSL.stringLiteral("Thirty"))));
  }

  @Test
  public void castAnalyzer() {
    assertAnalyzeEqual(
        dsl.castInt(DSL.ref("boolean_value", BOOLEAN)),
        AstDSL.cast(AstDSL.unresolvedAttr("boolean_value"), AstDSL.stringLiteral("INT"))
    );

    assertThrows(IllegalStateException.class, () -> analyze(AstDSL.cast(AstDSL.unresolvedAttr(
        "boolean_value"), AstDSL.stringLiteral("INTERVAL"))));
  }

  @Test
  public void case_with_default_result_type_different() {
    UnresolvedExpression caseWhen = AstDSL.caseWhen(
        qualifiedName("integer_value"),
        AstDSL.intLiteral(60),
        AstDSL.when(AstDSL.intLiteral(30), AstDSL.stringLiteral("Thirty")),
        AstDSL.when(AstDSL.intLiteral(50), AstDSL.stringLiteral("Fifty")));

    SemanticCheckException exception = assertThrows(
        SemanticCheckException.class, () -> analyze(caseWhen));
    assertEquals(
        "All result types of CASE clause must be the same, but found [STRING, STRING, INTEGER]",
        exception.getMessage());
  }

  @Test
  public void scalar_window_function() {
    assertAnalyzeEqual(
        dsl.rank(),
        AstDSL.window(AstDSL.function("rank"), emptyList(), emptyList()));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void aggregate_window_function() {
    assertAnalyzeEqual(
        new AggregateWindowFunction(dsl.avg(DSL.ref("integer_value", INTEGER))),
        AstDSL.window(
            AstDSL.aggregate("avg", qualifiedName("integer_value")),
            emptyList(),
            emptyList()));
  }

  @Test
  public void qualified_name_with_qualifier() {
    analysisContext.push();
    analysisContext.peek().define(new Symbol(Namespace.INDEX_NAME, "index_alias"), STRUCT);
    assertAnalyzeEqual(
        DSL.ref("integer_value", INTEGER),
        qualifiedName("index_alias", "integer_value")
    );

    analysisContext.peek().define(new Symbol(Namespace.FIELD_NAME, "object_field"), STRUCT);
    analysisContext.peek().define(new Symbol(Namespace.FIELD_NAME, "object_field.integer_value"),
        INTEGER);
    assertAnalyzeEqual(
        DSL.ref("object_field.integer_value", INTEGER),
        qualifiedName("object_field", "integer_value")
    );

    SyntaxCheckException exception =
        assertThrows(SyntaxCheckException.class,
            () -> analyze(qualifiedName("nested_field", "integer_value")));
    assertEquals(
        "The qualifier [nested_field] of qualified name [nested_field.integer_value] "
            + "must be an field name, index name or its alias",
        exception.getMessage()
    );
    analysisContext.pop();
  }

  @Test
  public void interval() {
    assertAnalyzeEqual(
        dsl.interval(DSL.literal(1L), DSL.literal("DAY")),
        AstDSL.intervalLiteral(1L, DataType.LONG, "DAY"));
  }

  @Test
  public void all_fields() {
    assertAnalyzeEqual(
        DSL.literal("*"),
        AllFields.of());
  }

  @Test
  public void case_clause() {
    assertAnalyzeEqual(
        DSL.cases(
            DSL.literal(ExprValueUtils.nullValue()),
            DSL.when(
                dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(30)),
                DSL.literal("test"))),
        AstDSL.caseWhen(
            AstDSL.nullLiteral(),
            AstDSL.when(
                AstDSL.function("=",
                    qualifiedName("integer_value"),
                    AstDSL.intLiteral(30)),
                AstDSL.stringLiteral("test"))));
  }

  @Test
  public void skip_array_data_type() {
    SyntaxCheckException exception =
        assertThrows(SyntaxCheckException.class,
            () -> analyze(qualifiedName("array_value")));
    assertEquals(
        "Identifier [array_value] of type [ARRAY] is not supported yet",
        exception.getMessage()
    );
  }

  @Test
  public void undefined_var_semantic_check_failed() {
    SemanticCheckException exception = assertThrows(SemanticCheckException.class,
        () -> analyze(
            AstDSL.and(AstDSL.unresolvedAttr("undefined_field"), AstDSL.booleanLiteral(true))));
    assertEquals("can't resolve Symbol(namespace=FIELD_NAME, name=undefined_field) in type env",
        exception.getMessage());
  }

  @Test
  public void undefined_aggregation_function() {
    SemanticCheckException exception = assertThrows(SemanticCheckException.class,
        () -> analyze(AstDSL.aggregate("ESTDC_ERROR", field("integer_value"))));
    assertEquals("Unsupported aggregation function ESTDC_ERROR", exception.getMessage());
  }

  @Test
  public void aggregation_filter() {
    assertAnalyzeEqual(
        dsl.avg(DSL.ref("integer_value", INTEGER))
            .condition(dsl.greater(DSL.ref("integer_value", INTEGER), DSL.literal(1))),
        AstDSL.filteredAggregate("avg", qualifiedName("integer_value"),
            function(">", qualifiedName("integer_value"), intLiteral(1)))
    );
  }

  @Test
  public void variance_mapto_varPop() {
    assertAnalyzeEqual(
        dsl.varPop(DSL.ref("integer_value", INTEGER)),
        AstDSL.aggregate("variance", qualifiedName("integer_value"))
    );
  }

  @Test
  public void distinct_count() {
    assertAnalyzeEqual(
        dsl.distinctCount(DSL.ref("integer_value", INTEGER)),
        AstDSL.distinctAggregate("count", qualifiedName("integer_value"))
    );
  }

  @Test
  public void filtered_distinct_count() {
    assertAnalyzeEqual(
        dsl.distinctCount(DSL.ref("integer_value", INTEGER))
            .condition(dsl.greater(DSL.ref("integer_value", INTEGER), DSL.literal(1))),
        AstDSL.filteredDistinctCount("count", qualifiedName("integer_value"), function(
            ">", qualifiedName("integer_value"), intLiteral(1)))
    );
  }

  @Test
  public void named_argument() {
    assertAnalyzeEqual(
        dsl.namedArgument("arg_name", DSL.literal("query")),
        AstDSL.unresolvedArg("arg_name", stringLiteral("query"))
    );
  }

  @Test
  public void named_parse_expression() {
    analysisContext.push();
    analysisContext.peek().define(new Symbol(Namespace.FIELD_NAME, "string_field"), STRING);
    analysisContext.getNamedParseExpressions()
        .add(DSL.named("group",
            DSL.parsed(ref("string_field", STRING), DSL.literal("(?<group>\\d+)"),
            DSL.literal("group"))));
    assertAnalyzeEqual(
        DSL.parsed(ref("string_field", STRING), DSL.literal("(?<group>\\d+)"),
            DSL.literal("group")),
        qualifiedName("group")
    );
  }

  @Test
  public void named_non_parse_expression() {
    analysisContext.push();
    analysisContext.peek().define(new Symbol(Namespace.FIELD_NAME, "string_field"), STRING);
    analysisContext.getNamedParseExpressions()
        .add(DSL.named("string_field", DSL.literal("123")));
    assertAnalyzeEqual(DSL.ref("string_field", STRING), qualifiedName("string_field"));
  }

  @Test
  void match_bool_prefix_expression() {
    assertAnalyzeEqual(
        dsl.match_bool_prefix(
            dsl.namedArgument("field", DSL.literal("fieldA")),
            dsl.namedArgument("query", DSL.literal("sample query"))),
        AstDSL.function("match_bool_prefix",
            AstDSL.unresolvedArg("field", stringLiteral("fieldA")),
            AstDSL.unresolvedArg("query", stringLiteral("sample query"))));
  }

  @Test
  void visit_span() {
    assertAnalyzeEqual(
        DSL.span(DSL.ref("integer_value", INTEGER), DSL.literal(1), ""),
        AstDSL.span(qualifiedName("integer_value"), intLiteral(1), SpanUnit.NONE)
    );
  }

  @Test
  void visit_in() {
    assertAnalyzeEqual(
        dsl.or(
            dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(1)),
            dsl.or(
                dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(2)),
                dsl.equal(DSL.ref("integer_value", INTEGER), DSL.literal(3)))),
        AstDSL.in(field("integer_value"), intLiteral(1), intLiteral(2), intLiteral(3)));

    assertThrows(
        SemanticCheckException.class,
        () -> analyze(AstDSL.in(field("integer_value"), Collections.emptyList())));
  }

  @Test
  void multi_match_expression() {
    assertAnalyzeEqual(
        dsl.multi_match(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field", ExprValueUtils.floatValue(1.F)))))),
            dsl.namedArgument("query", DSL.literal("sample query"))),
        AstDSL.function("multi_match",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(Map.of(
                "field", 1.F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query"))));
  }

  @Test
  void multi_match_expression_with_params() {
    assertAnalyzeEqual(
        dsl.multi_match(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field", ExprValueUtils.floatValue(1.F)))))),
            dsl.namedArgument("query", DSL.literal("sample query")),
            dsl.namedArgument("analyzer", DSL.literal("keyword"))),
        AstDSL.function("multi_match",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(Map.of(
                "field", 1.F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query")),
            AstDSL.unresolvedArg("analyzer", stringLiteral("keyword"))));
  }

  @Test
  void multi_match_expression_two_fields() {
    assertAnalyzeEqual(
        dsl.multi_match(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field1", ExprValueUtils.floatValue(1.F),
                    "field2", ExprValueUtils.floatValue(.3F)))))),
            dsl.namedArgument("query", DSL.literal("sample query"))),
        AstDSL.function("multi_match",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(ImmutableMap.of(
                "field1", 1.F, "field2", .3F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query"))));
  }

  @Test
  void simple_query_string_expression() {
    assertAnalyzeEqual(
        dsl.simple_query_string(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field", ExprValueUtils.floatValue(1.F)))))),
            dsl.namedArgument("query", DSL.literal("sample query"))),
        AstDSL.function("simple_query_string",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(Map.of(
                "field", 1.F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query"))));
  }

  @Test
  void simple_query_string_expression_with_params() {
    assertAnalyzeEqual(
        dsl.simple_query_string(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field", ExprValueUtils.floatValue(1.F)))))),
            dsl.namedArgument("query", DSL.literal("sample query")),
            dsl.namedArgument("analyzer", DSL.literal("keyword"))),
        AstDSL.function("simple_query_string",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(Map.of(
                "field", 1.F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query")),
            AstDSL.unresolvedArg("analyzer", stringLiteral("keyword"))));
  }

  @Test
  void simple_query_string_expression_two_fields() {
    assertAnalyzeEqual(
        dsl.simple_query_string(
            dsl.namedArgument("fields", DSL.literal(
                new ExprTupleValue(new LinkedHashMap<>(ImmutableMap.of(
                    "field1", ExprValueUtils.floatValue(1.F),
                    "field2", ExprValueUtils.floatValue(.3F)))))),
            dsl.namedArgument("query", DSL.literal("sample query"))),
        AstDSL.function("simple_query_string",
            AstDSL.unresolvedArg("fields", new RelevanceFieldList(ImmutableMap.of(
                "field1", 1.F, "field2", .3F))),
            AstDSL.unresolvedArg("query", stringLiteral("sample query"))));
  }

  @Test
  public void match_phrase_prefix_all_params() {
    assertAnalyzeEqual(
        dsl.match_phrase_prefix(
            dsl.namedArgument("field", "test"),
            dsl.namedArgument("query", "search query"),
            dsl.namedArgument("slop", "3"),
            dsl.namedArgument("boost", "1.5"),
            dsl.namedArgument("analyzer", "standard"),
            dsl.namedArgument("max_expansions", "4"),
            dsl.namedArgument("zero_terms_query", "NONE")
            ),
        AstDSL.function("match_phrase_prefix",
          unresolvedArg("field", stringLiteral("test")),
          unresolvedArg("query", stringLiteral("search query")),
          unresolvedArg("slop", stringLiteral("3")),
          unresolvedArg("boost", stringLiteral("1.5")),
          unresolvedArg("analyzer", stringLiteral("standard")),
          unresolvedArg("max_expansions", stringLiteral("4")),
          unresolvedArg("zero_terms_query", stringLiteral("NONE"))
          )
    );
  }

  protected Expression analyze(UnresolvedExpression unresolvedExpression) {
    return expressionAnalyzer.analyze(unresolvedExpression, analysisContext);
  }

  protected void assertAnalyzeEqual(Expression expected,
                                    UnresolvedExpression unresolvedExpression) {
    assertEquals(expected, analyze(unresolvedExpression));
  }

  protected void assertAnalyzeEqual(Expression expected,
                                    UnresolvedPlan unresolvedPlan) {
    assertEquals(expected, analyze(unresolvedPlan));
  }
}
