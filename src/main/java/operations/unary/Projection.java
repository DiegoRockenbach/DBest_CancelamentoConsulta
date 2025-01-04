package operations.unary;

import com.mxgraph.model.mxCell;
import entities.Column;
import entities.cells.Cell;
import entities.cells.OperationCell;
import entities.utils.cells.CellUtils;
import enums.OperationErrorType;
import exceptions.tree.TreeException;
import operations.IOperator;
import operations.Operation;
import operations.OperationErrorVerifier;

import java.util.List;
import java.util.Optional;

public class Projection implements IOperator {

    @Override
    public void executeOperation(mxCell jCell, List<String> arguments, String alias) {
        Optional<Cell> optionalCell = CellUtils.getActiveCell(jCell);

        if (optionalCell.isEmpty()) return;

        OperationCell cell = (OperationCell) optionalCell.get();
        OperationErrorType errorType = null;

        try {
            errorType = OperationErrorType.NO_PARENT;
            OperationErrorVerifier.hasParent(cell);

            errorType = OperationErrorType.NO_ONE_PARENT;
            OperationErrorVerifier.oneParent(cell);

            errorType = OperationErrorType.PARENT_ERROR;
            OperationErrorVerifier.noParentError(cell);

            errorType = OperationErrorType.NULL_ARGUMENT;
            OperationErrorVerifier.noNullArgument(arguments);

            errorType = OperationErrorType.EMPTY_ARGUMENT;
            OperationErrorVerifier.noEmptyArgument(arguments);

            errorType = OperationErrorType.PARENT_WITHOUT_COLUMN;
            OperationErrorVerifier.parentContainsColumns(cell.getParents().get(0).getColumnSourcesAndNames(), arguments);

            errorType = null;
        } catch (TreeException exception) {
            cell.setError(errorType);
        }

		if (errorType != null) return;

        Cell parentCell = cell.getParents().get(0);

        List<String> fixedArguments = Column.composeSourceAndName(arguments, parentCell);

        ibd.query.Operation operator = parentCell.getOperator();

        ibd.query.Operation filterColumns = null;
        try {
            filterColumns = new ibd.query.unaryop.Projection(operator, fixedArguments.toArray(new String[0]));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //Operator readyOperator = new DistinctOperator(filterColumns);

        String operationName = String.format("%s %s", cell.getType().symbol, fixedArguments);

        Operation.operationSetter(cell, operationName, fixedArguments, filterColumns);
    }
}
