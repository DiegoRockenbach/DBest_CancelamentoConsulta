package enums;

import java.util.Arrays;
import java.util.List;

import controllers.ConstantController;

import entities.Action.CreateOperationCellAction;

import gui.frames.forms.operations.BooleanExpressionForm;
import gui.frames.forms.operations.IOperationForm;
import gui.frames.forms.operations.JoinForm;
import gui.frames.forms.operations.unary.AggregationForm;
import gui.frames.forms.operations.unary.GroupForm;
import gui.frames.forms.operations.unary.LimitForm;
import gui.frames.forms.operations.unary.ProjectionForm;
import gui.frames.forms.operations.unary.RenameForm;
import gui.frames.forms.operations.unary.SortForm;

import operations.IOperator;
import operations.binary.CartesianProduct;
import operations.binary.joins.AntiJoin;
import operations.binary.joins.Join;
import operations.binary.joins.LeftJoin;
import operations.binary.joins.RightJoin;
import operations.binary.joins.SemiJoin;
import operations.binary.set.Difference;
import operations.binary.set.Intersection;
import operations.binary.set.Union;
import operations.unary.Aggregation;
import operations.unary.DuplicateRemoval;
import operations.unary.Group;
import operations.unary.Hash;
import operations.unary.Limit;
import operations.unary.SelectColumns;
import operations.unary.Projection;
import operations.unary.Rename;
import operations.unary.Selection;
import operations.unary.Sort;

public enum OperationType {

    SELECTION         (ConstantController.getString("operation.selection"), "σ", "selection", "selection[args](source)", OperationArity.UNARY, BooleanExpressionForm.class, Selection.class, false),
    PROJECTION        (ConstantController.getString("operation.projection"), "π", "projection", "projection[args](source)", OperationArity.UNARY, ProjectionForm.class, Projection.class, true),
    SELECT_COLUMNS(ConstantController.getString("operation.selectColumns"), "S", "selectColumns", "selectColumns[args](source)", OperationArity.UNARY, ProjectionForm.class, SelectColumns.class, false),
    LIMIT(ConstantController.getString("operation.limit"), "L", "limit", "limit[args](source)", OperationArity.UNARY, LimitForm.class, Limit.class, false),
DUPLICATE_REMOVAL(ConstantController.getString("operation.duplicateRemoval"), "\u0394", "duplicateRemoval", "duplicateRemoval(source)", OperationArity.UNARY, null, DuplicateRemoval.class, false),
    RENAME            (ConstantController.getString("operation.rename"), "ρ", "rename", "rename[args](source)", OperationArity.UNARY, RenameForm.class, Rename.class, false),
//    GROUP             (ConstantController.getString("operation.group"), "G", "group", "group[args](relation)", OperationArity.UNARY, GroupForm.class, Group.class, NO_ONE_ARGUMENT, PARENT_WITHOUT_COLUMN, NO_PREFIX),
    GROUP             (ConstantController.getString("operation.group"), "\u22CA", "group", "group[args](relation)", OperationArity.UNARY, GroupForm.class, Group.class, false),
//    AGGREGATION       (ConstantController.getString("operation.aggregation"), "\u22C8", "aggregation", "aggregation[args](relation)", OperationArity.UNARY, AggregationForm.class, Aggregation.class, NO_ONE_ARGUMENT, PARENT_WITHOUT_COLUMN, NO_PREFIX),
        AGGREGATION       (ConstantController.getString("operation.aggregation"), "G", "aggregation", "aggregation[args](relation)", OperationArity.UNARY, AggregationForm.class, Aggregation.class, false),
    SORT              (ConstantController.getString("operation.sort"), "↕", "sort", "sort[args](relation)", OperationArity.UNARY, SortForm.class, Sort.class, true),
//    INDEXER           (ConstantController.getString("operation.indexer"), "❶", "indexer", "indexer[args](source)", OperationArity.UNARY, IndexerForm.class, Indexer.class),
    JOIN              (ConstantController.getString("operation.join"), "|X|", "join", "join[args](source1,source2)", OperationArity.BINARY, JoinForm.class, Join.class, false),
    SEMI_JOIN              (ConstantController.getString("operation.semiJoin"), "\u22C9", "semiJoin", "semiJoin[args](source1,source2)", OperationArity.BINARY, BooleanExpressionForm.class, SemiJoin.class, false),
    ANTI_JOIN              (ConstantController.getString("operation.antiJoin"), "\u25B7", "antiJoin", "antiJoin[args](source1,source2)", OperationArity.BINARY, BooleanExpressionForm.class, AntiJoin.class, false),
    LEFT_JOIN         (ConstantController.getString("operation.leftJoin"), "⟕", "leftJoin", "leftJoin[args](source1,source2)", OperationArity.BINARY, BooleanExpressionForm.class, LeftJoin.class, false),
    RIGHT_JOIN        (ConstantController.getString("operation.rightJoin"), "⟖", "rightJoin", "rightJoin[args](source1,source2)", OperationArity.BINARY, BooleanExpressionForm.class, RightJoin.class, false),
    CARTESIAN_PRODUCT (ConstantController.getString("operation.cartesianProduct"), "✕", "cartesianProduct", "cartesianProduct(source1,source2)", OperationArity.BINARY, null, CartesianProduct.class, false),
    HASH             (ConstantController.getString("operation.hash"), "#", "hash", "hash[args](source)", OperationArity.UNARY, null, Hash.class,true),
    UNION             (ConstantController.getString("operation.union"), "∪", "union", "union(source1,source2)", OperationArity.BINARY, null, Union.class,true),
    INTERSECTION      (ConstantController.getString("operation.intersection"), "∩", "intersection", "intersection(source1,source2)", OperationArity.BINARY, null, Intersection.class, true),
    DIFFERENCE        (ConstantController.getString("operation.difference"), "-", "difference", "difference(source1,source2)", OperationArity.BINARY, null, Difference.class, true);

    public final String displayName;

    public final String symbol;

    public final String name;

    public final String dslSyntax;

    public final OperationArity arity;

    public final Class<? extends IOperationForm> form;

    public final Class<? extends IOperator> operatorClass;

    public final boolean isSetBasedProcessing;

    public static final List<OperationType> OPERATIONS_WITHOUT_FORM = Arrays
        .stream(values())
        .sequential()
        .filter(operationType -> operationType.form == null)
        .toList();

    OperationType(
        String displayName, String symbol, String name, String dslSyntax, OperationArity arity,
        Class<? extends IOperationForm> form, Class<? extends IOperator> operatorClass, boolean isSetBasedProcessing
    ) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.name = name;
        this.dslSyntax = dslSyntax;
        this.arity = arity;
        this.form = form;
        this.operatorClass = operatorClass;
        this.isSetBasedProcessing = isSetBasedProcessing;
    }

    public String getFormattedDisplayName() {
        return String.format("%s %s", this.symbol, this.displayName);
    }

    public static OperationType fromString(String operationTypeName) {
        for (OperationType operationType : OperationType.values()) {
            if (operationType.name.equalsIgnoreCase(operationTypeName)) {
                return operationType;
            }
        }

        throw new IllegalArgumentException(String.format("Invalid operation type: %s", operationTypeName));
    }

    public CreateOperationCellAction getAction() {
        return new CreateOperationCellAction(this);
    }
}
