package sks;

import java.util.ArrayList;


/**
 * QueryTextPredicate
 * <p>
 * Load a data set to build index
 *
 * @author Ariel Cary
 * @version $Id: Loader.java,v 1.0 May 26, 2008
 */
public class QueryTextPredicate {


 /**
 * A list of keywords.
 */
  private ArrayList<String> keywordList;


 /**
 * Comparison operator.
 */
  private ComparisonOperator op;

  /**
  * Field number. "-1" is anyfield.
  */
  private short fieldNumber = -1;

  private String fieldName = "";

  public QueryTextPredicate() {
    this(ComparisonOperator.EQUAL, (short) -1, "");
  }


  public QueryTextPredicate(ComparisonOperator op, short fieldNbr, String fieldName) {
    this.keywordList = new ArrayList<String>();
    this.op = op;
    this.fieldNumber = fieldNbr;
    this.fieldName = fieldName;
  }
 
  /**
  * add a keyword into keyword list
  */
  public void add(String keyword) {
    keywordList.add(keyword);
  }

  /**
  * set the Operator
  */
  public void setOperator(ComparisonOperator op) {
    this.op = op;
  }

  /**
  * set the fieldNbr
  */
  public void setFieldNbr(short fieldNbr) {
    this.fieldNumber = fieldNbr;
  }

  public ArrayList<String> getKeywordList() {
    return keywordList;
  }

  public ComparisonOperator getOperator() {
    return op;
  }

  public short getFieldNumber() {
    return fieldNumber;
  }

  @Override
  public String toString() {
    return "<" + ((fieldNumber == -1)? "any":fieldName) + ", " + op.toString() +
            ", " + keywordList.toString() + ">";
  }
}