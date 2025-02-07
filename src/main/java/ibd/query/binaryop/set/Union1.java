/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibd.query.binaryop.set;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.query.fields.Field;
import java.util.Iterator;
import java.util.List;

/**
 * Performs a difference between the left and the right operations.
 *
 * @author Sergio
 */
public class Union1 extends Set {

    /**
     *
     * @param leftOperation the left side operation
     * @param rightOperation the right side operation
     * @throws Exception
     */
    public Union1(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }
    
    protected Prototype setPrototype() throws Exception {
        Prototype prototype = new Prototype();
        int leftSize = getColumnsSize(leftOperation);
        int rightSize = getColumnsSize(rightOperation);
        int minSize = Math.min(leftSize, rightSize);

        int currentColumn = 0;

        for (ReferedDataSource dataSource : getLeftOperation().getExposedDataSources()) {
            List<Column> columns = dataSource.prototype.getColumns();
            int columnsToCopy = Math.min(columns.size(), minSize - currentColumn);

            for (int i = 0; i < columnsToCopy; i++) {
                Column originalCol = columns.get(i);
                Column newCol = Prototype.cloneColumn(originalCol);
                prototype.addColumn(newCol);
                currentColumn++;
            }

            if (currentColumn >= minSize) {
                break;
            }
        }

        return prototype;
        
    }

    protected int getColumnsSize(Operation op) throws Exception {
        int colSize = 0;
        for (ReferedDataSource dataSource : op.getExposedDataSources()) {
            colSize += dataSource.prototype.getColumns().size();
        }
        return colSize;
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;

        connectedDataSources[0].prototype = setPrototype();

    }
    
    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = connectedDataSources;

    }

    /**
     *
     * @return the name of the operation
     */
    @Override
    public String toString() {
        return "Union";
    }

    /**
     * {@inheritDoc }
     *
     * @return an iterator that performs a simple nested loop join over the
     * tuples from the left and right sides
     */
    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new UnionIterator(processedTuples, withFilterDelegation);
    }

    /**
     * the class that produces resulting tuples from the union between the two
     * underlying operations.
     */
    private class UnionIterator extends UnpagedOperationIterator {

        //keeps the current state of the left side of the join: The current tuple read
        Tuple leftTuple;

        Tuple rightTuple;
        //the iterator over the operation on the left side
        Iterator<Tuple> leftTuples;
        //the iterator over the operation on the left side
        Iterator<Tuple> rightTuples;

        /**
         * @param processedTuples the tuples that come from operations already
         * processed. The columns from these tuples can be used by the
         * unprocessed operations, like for filtering.
         * @param lookup the condition from the parent operation that needs to
         * be satisfied
         */
        public UnionIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuple = null;
            leftTuples = leftOperation.lookUp(processedTuples, false); //iterate over all tuples from the left side
            rightTuples = rightOperation.lookUp(processedTuples, false); //iterate over all tuples from the left side
        }

        /**
         *
         * @return the next satisfying tuple, if any
         */
        @Override
        protected Tuple findNextTuple() {

            if (leftTuple == null) {
                if (leftTuples.hasNext()) {
                    while (leftTuples.hasNext()) {
                        leftTuple = leftTuples.next();
                        break;
                    }
                }
            }

            if (rightTuple == null) {
                while (rightTuples.hasNext()) {
                    rightTuple = rightTuples.next();
                    break;
                }
            }

            if (leftTuple == null && rightTuple == null) {
                return null;
            }

            if (leftTuple != null && rightTuple != null
                    && (leftTuple.compareTo(rightTuple) == 0)) {
                Tuple returnTp = new Tuple();
                //returnTp.setSourceRows(leftTuple);
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);
                leftTuple = null;
                rightTuple = null;
                return returnTp;
            } else if (leftTuple != null && (rightTuple == null
                    || (leftTuple.compareTo(rightTuple) < 0))) {
                Tuple returnTp = new Tuple();
                //returnTp.setSourceRows(leftTuple);
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);
                leftTuple = null;
                return returnTp;
            } else {
                Tuple returnTp = new Tuple();
                //tuple.setSourceRows(rightTuple);
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(rightTuple);
                rightTuple = null;
                return returnTp;
            }
        }

//        private int compare(Tuple t1, Tuple t2) {
//            for (int i = 0; i < t1.rows.length; i++) {
//                LinkedDataRow st1 = t1.rows[i];
//                LinkedDataRow st2 = t2.rows[i];
//                for (int j = 0; i < st1.getFieldsSize(); i++) {
//                    Field f1 = st1.getField(j);
//                    Field f2 = st2.getField(j);
//                    int comp = f1.compareTo(f2);
//                    if (comp != 0) {
//                        return comp;
//                    }
//                }
//            }
//            return 0;
//        }

    }
    
    protected LinkedDataRow buildRow(Tuple tuple) {
        List<Column> columns = dataSources[0].prototype.getColumns();
        LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
        int totalColumns = columns.size();
        int currentIndex = 0;

        outerLoop:
        for (LinkedDataRow row1 : tuple.rows) {
            int fieldSize = row1.getFieldsSize();
            for (int i = 0; i < fieldSize; i++) {
                Comparable value = row1.getValue(i);
                row.setValue(currentIndex, value);
                currentIndex++;
                if (currentIndex == totalColumns) {
                    break outerLoop;
                }
            }
        }

        return row;
    }

}
