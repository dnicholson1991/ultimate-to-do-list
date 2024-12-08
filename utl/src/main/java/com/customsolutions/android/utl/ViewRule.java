package com.customsolutions.android.utl;

// The ViewRule class (and it's subclasses) implement the filtering rules for UTL views.

public abstract class ViewRule 
{
    // Each subclass must implement a constructor that takes an encoded database string
    // as input.  The database string is parsed and a class instance is returned.
    
    // Each subclass must also implement a constructor that takes a variety of inputs 
    // from an on-screen form.
    
    // This is the database field that the rule is for.  Each subclass must set this in
    // the constructor.
    protected String dbField;
    
    // Get a human-readable string for the rule:
    public abstract String getReadableString();
    
    // Get a string for the rule that can be inserted into the database:
    public abstract String getDatabaseString();
    
    // Get a SQL "where" statement for the tasks table.  The "where" itself is not 
    // included.  The statement is wrapped in parenthesis to allow it to be joined
    // with other statements.
    public abstract String getSQLWhere();
    
    // Return true of the "where" clause is actually a "having" clause.
    // A subclass must override this if it returns a having clause.
    public boolean isHavingClause()
    {
    	return false;
    }
    
    // Get the database field this rule is for:
    public String getDbField()
    {
        return dbField;
    }
}
