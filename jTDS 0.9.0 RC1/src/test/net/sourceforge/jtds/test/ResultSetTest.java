package net.sourceforge.jtds.test;

import java.sql.*;

/**
 * @version 1.0
 */
public class ResultSetTest extends TestBase {
    public ResultSetTest(String name) {
        super(name);
    }

    /**
     * Test BIT data type.
     */
    public void testGetObject1() throws Exception {
        boolean data = true;

        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #getObject1 (data BIT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #getObject1 (data) VALUES (?)");

        pstmt.setBoolean(1, data);
        assertTrue(pstmt.executeUpdate() == 1);

        pstmt.close();

        Statement stmt2 = con.createStatement();
        ResultSet rs = stmt2.executeQuery("SELECT data FROM #getObject1");

        assertTrue(rs.next());

        assertTrue(data == rs.getBoolean(1));
        assertTrue(rs.getByte(1) == 1);
        assertTrue(rs.getShort(1) == 1);
        assertTrue(rs.getInt(1) == 1);
        assertTrue(rs.getLong(1) == 1);
        assertTrue(rs.getFloat(1) == 1);
        assertTrue(rs.getDouble(1) == 1);
        assertTrue(rs.getBigDecimal(1).byteValue() == 1);
        assertTrue("1".equals(rs.getString(1)));

        Object tmpData = rs.getObject(1);

        assertTrue(tmpData instanceof Boolean);
        assertTrue(data == ((Boolean) tmpData).booleanValue());

        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        
        assertTrue(resultSetMetaData != null);
        assertTrue(resultSetMetaData.getColumnType(1) == Types.BIT);
        
        assertTrue(!rs.next());
        stmt2.close();
        rs.close();
    }

    /**
     * Test TINYINT data type.
     */
    public void testGetObject2() throws Exception {
        byte data = 1;

        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #getObject2 (data TINYINT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #getObject2 (data) VALUES (?)");

        pstmt.setByte(1, data);
        assertTrue(pstmt.executeUpdate() == 1);

        pstmt.close();

        Statement stmt2 = con.createStatement();
        ResultSet rs = stmt2.executeQuery("SELECT data FROM #getObject2");

        assertTrue(rs.next());

        assertTrue(rs.getBoolean(1));
        assertTrue(rs.getByte(1) == 1);
        assertTrue(rs.getShort(1) == 1);
        assertTrue(rs.getInt(1) == 1);
        assertTrue(rs.getLong(1) == 1);
        assertTrue(rs.getFloat(1) == 1);
        assertTrue(rs.getDouble(1) == 1);
        assertTrue(rs.getBigDecimal(1).byteValue() == 1);
        assertTrue("1".equals(rs.getString(1)));

        Object tmpData = rs.getObject(1);

        assertTrue(tmpData instanceof Byte);
        assertTrue(data == ((Byte) tmpData).byteValue());

        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        
        assertTrue(resultSetMetaData != null);
        assertTrue(resultSetMetaData.getColumnType(1) == Types.TINYINT);
        
        assertTrue(!rs.next());
        stmt2.close();
        rs.close();
    }

    /**
     * Test SMALLINT data type.
     */
    public void testGetObject3() throws Exception {
        short data = 1;

        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #getObject3 (data SMALLINT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #getObject3 (data) VALUES (?)");

        pstmt.setShort(1, data);
        assertTrue(pstmt.executeUpdate() == 1);

        pstmt.close();

        Statement stmt2 = con.createStatement();
        ResultSet rs = stmt2.executeQuery("SELECT data FROM #getObject3");

        assertTrue(rs.next());

        assertTrue(rs.getBoolean(1));
        assertTrue(rs.getByte(1) == 1);
        assertTrue(rs.getShort(1) == 1);
        assertTrue(rs.getInt(1) == 1);
        assertTrue(rs.getLong(1) == 1);
        assertTrue(rs.getFloat(1) == 1);
        assertTrue(rs.getDouble(1) == 1);
        assertTrue(rs.getBigDecimal(1).shortValue() == 1);
        assertTrue("1".equals(rs.getString(1)));

        Object tmpData = rs.getObject(1);

        assertTrue(tmpData instanceof Short);
        assertTrue(data == ((Short) tmpData).shortValue());

        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        
        assertTrue(resultSetMetaData != null);
        assertTrue(resultSetMetaData.getColumnType(1) == Types.SMALLINT);
        
        assertTrue(!rs.next());
        stmt2.close();
        rs.close();
    }

    /**
     * Test INT data type.
     */
    public void testGetObject4() throws Exception {
        int data = 1;

        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #getObject4 (data INT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #getObject4 (data) VALUES (?)");

        pstmt.setInt(1, data);
        assertTrue(pstmt.executeUpdate() == 1);

        pstmt.close();

        Statement stmt2 = con.createStatement();
        ResultSet rs = stmt2.executeQuery("SELECT data FROM #getObject4");

        assertTrue(rs.next());

        assertTrue(rs.getBoolean(1));
        assertTrue(rs.getByte(1) == 1);
        assertTrue(rs.getShort(1) == 1);
        assertTrue(rs.getInt(1) == 1);
        assertTrue(rs.getLong(1) == 1);
        assertTrue(rs.getFloat(1) == 1);
        assertTrue(rs.getDouble(1) == 1);
        assertTrue(rs.getBigDecimal(1).intValue() == 1);
        assertTrue("1".equals(rs.getString(1)));

        Object tmpData = rs.getObject(1);

        assertTrue(tmpData instanceof Integer);
        assertTrue(data == ((Integer) tmpData).intValue());

        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        
        assertTrue(resultSetMetaData != null);
        assertTrue(resultSetMetaData.getColumnType(1) == Types.INTEGER);
        
        assertTrue(!rs.next());
        stmt2.close();
        rs.close();
    }

    /**
     * Test for bug [961594] ResultSet.
     */
    public void testResultSetScroll1() throws Exception {
    	int count = 125;
    	
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #resultSetScroll1 (data INT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #resultSetScroll1 (data) VALUES (?)");

        for (int i = 1; i <= count; i++) {
        	pstmt.setInt(1, i);
        	assertTrue(pstmt.executeUpdate() == 1);
        }

        pstmt.close();

        Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
        		ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = stmt2.executeQuery("SELECT data FROM #resultSetScroll1");

        assertTrue(rs.last());
        assertEquals(count, rs.getRow());
        
        stmt2.close();
        rs.close();
    }

    /**
     * Test for bug [945462] getResultSet() return null if you use scrollable/updatable
     */
    public void testResultSetScroll2() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #resultSetScroll2 (data INT)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("INSERT INTO #resultSetScroll2 (data) VALUES (?)");

        pstmt.setInt(1, 1);
        assertTrue(pstmt.executeUpdate() == 1);

        pstmt.close();

        Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_UPDATABLE);
        stmt2.executeQuery("SELECT data FROM #resultSetScroll2");

        ResultSet rs = stmt2.getResultSet();
        
        assertTrue(rs != null);
        assertTrue(rs.next());
        assertEquals(rs.getInt(1), 1);
        assertTrue(!rs.next());
        
        stmt2.close();
        rs.close();
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(ResultSetTest.class);
    }
}