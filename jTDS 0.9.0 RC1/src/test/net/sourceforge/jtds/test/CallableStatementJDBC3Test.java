package net.sourceforge.jtds.test;

import java.sql.*;

/**
 * Test the JDBC 3.0 features of CallableStatement.
 *
 * @author  Alin Sinpalean
 * @created 04/07/2004
 */
public class CallableStatementJDBC3Test extends TestBase {
    public CallableStatementJDBC3Test(String name) {
        super(name);
    }

    /**
     * Test named parameters.
     */
    public void testNamedParameters0001() throws Exception {
        final String data = "New {order} plus {1} more";
        final String outData = "test";
        Statement stmt = con.createStatement();

        stmt.execute("CREATE TABLE #csn1 (data VARCHAR(32))");
        stmt.close();

        stmt = con.createStatement();
        stmt.execute("create procedure #sp_csn1 @data VARCHAR(32) OUT as "
                     + "INSERT INTO #csn1 (data) VALUES(@data) "
                     + "SET @data = '" + outData + "'"
                     + "RETURN 13");
        stmt.close();

        CallableStatement cstmt = con.prepareCall("{?=call #sp_csn1(?)}");

        cstmt.registerOutParameter("@return_status", Types.INTEGER);
        cstmt.setString("@data", data);
        cstmt.registerOutParameter("@data", Types.VARCHAR);
        assertEquals(1, cstmt.executeUpdate());
        assertFalse(cstmt.getMoreResults());
        assertEquals(-1, cstmt.getUpdateCount());
        assertEquals(outData, cstmt.getString("@data"));
        cstmt.close();

        stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT data FROM #csn1");

        assertTrue(rs.next());
        assertEquals(data, rs.getString(1));
        assertTrue(!rs.next());

        rs.close();
    }

    /**
     * Test for bug [946171] null boolean in CallableStatement bug
     */
    public void testCallableRegisterOutParameter1() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("create procedure rop1 @bool bit, @whatever int OUTPUT as\r\n "
                     + "begin\r\n"
                     + "set @whatever = 1\r\n"
                     + "end");
        stmt.close();
    	
        try {
            CallableStatement cstmt = con.prepareCall("{call rop1(?,?)}");

            cstmt.setNull(1, Types.BOOLEAN);
            cstmt.registerOutParameter(2, Types.INTEGER);
            cstmt.execute();

            assertTrue(cstmt.getInt(2) == 1);
            cstmt.close();
        } finally {
            stmt = con.createStatement();
            stmt.execute("drop procedure rop1");
            stmt.close();
        }
    }

    /**
     * Test for bug [992715] wasnull() always returns false
     */
    public void testCallableRegisterOutParameter2() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("create procedure #rop2 @bool bit, @whatever varchar(1) OUTPUT as\r\n "
                     + "begin\r\n"
                     + "set @whatever = null\r\n"
                     + "end");
        stmt.close();
        
        CallableStatement cstmt = con.prepareCall("{call #rop2(?,?)}");

        cstmt.setNull(1, Types.BOOLEAN);
        cstmt.registerOutParameter(2, Types.VARCHAR);
        cstmt.execute();

        assertTrue(cstmt.getString(2) == null);
        assertTrue(cstmt.wasNull());
        cstmt.close();
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(CallableStatementJDBC3Test.class);
    }
}
