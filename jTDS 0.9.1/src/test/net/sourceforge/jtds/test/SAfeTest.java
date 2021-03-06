/*
 * SAfeTest.java
 *
 * Created on 08/23/2002
 */

package net.sourceforge.jtds.test;

import java.sql.*;
import java.math.BigDecimal;

import junit.framework.TestSuite;
import net.sourceforge.jtds.util.Logger;
import net.sourceforge.jtds.jdbc.Driver;
import net.sourceforge.jtds.jdbc.Messages;

import java.text.SimpleDateFormat;

/**
 * @author  Alin Sinpalean
 * @version 1.0
 * @since   0.4
 */
public class SAfeTest extends DatabaseTestCase {
    public SAfeTest(String name) {
        super(name);
    }

    public static void main(String args[]) {
        Logger.setActive(true);

        if (args.length > 0) {
            junit.framework.TestSuite s = new TestSuite();

            for (int i=0; i<args.length; i++) {
                s.addTest(new SAfeTest(args[i]));
            }

            junit.textui.TestRunner.run(s);
        } else {
            junit.textui.TestRunner.run(SAfeTest.class);
        }
    }

    /**
     * Test whether NULL values, 0-length strings and single space strings
     * are treated right.
     */
    public void testNullLengthStrings0001() throws Exception {
        String types[] = {
            "VARCHAR(50)",
            "TEXT",
            "VARCHAR(350)",
            "NVARCHAR(50)",
            "NTEXT",
        };

        String values[] = {
            null,
            "",
            " ",
            "x"
        };

        Statement stmt = con.createStatement();
        boolean tds70orLater = props.getProperty(Messages.get(Driver.TDS)) == null
            || props.getProperty(Messages.get(Driver.TDS)).charAt(0) >= '7';
        int typeCnt = tds70orLater ? types.length : 2;

        for (int i = 0; i < typeCnt; i++) {
            assertEquals(0, stmt.executeUpdate("CREATE TABLE #SAfe0001 (val " + types[i] + " NULL)"));

            for (int j = 0; j < values.length; j++) {
                String insQuery = values[j]==null ?
                    "INSERT INTO #SAfe0001 VALUES (NULL)" :
                    "INSERT INTO #SAfe0001 VALUES ('"+values[j]+"')";
                assertEquals(1, stmt.executeUpdate(insQuery));
                ResultSet rs = stmt.executeQuery("SELECT val FROM #SAfe0001");

                assertTrue(rs.next());

                if (tds70orLater || !" ".equals(values[j])) {
                    assertEquals(values[j], rs.getString(1));
                } else {
                    if (values[j] == null) {
                        assertEquals(null, rs.getObject(1));
                    } else {
                        assertEquals("", rs.getString(1));
                    }
                }

                assertTrue(!rs.next());
                assertEquals(0, stmt.executeUpdate("TRUNCATE TABLE #SAfe0001"));
            }

            assertEquals(0, stmt.executeUpdate("DROP TABLE #SAfe0001"));
        }
        stmt.close();
    }

    /**
     * Test cancelling. Create 2 connections, lock some records on one of them
     * and try to read them using the other one. Then, try executing some other
     * queries on the second connection to make sure it's in a correct state.
     */
    public void testCancel0002() throws Exception {
        // Create another connection to make sure the 2 statements use the same
        // physical connection
        Connection con2 = getConnection();

        Statement stmt = con.createStatement();
        assertTrue(!stmt.execute(
            "create table ##SAfe0002 (id int primary key, val varchar(20) null) "+
            "insert into ##SAfe0002 values (1, 'Line 1') "+
            "insert into ##SAfe0002 values (2, 'Line 2')"));
        assertEquals(0, stmt.getUpdateCount());
        assertTrue(!stmt.getMoreResults());
        assertEquals(1, stmt.getUpdateCount());
        assertTrue(!stmt.getMoreResults());
        assertEquals(1, stmt.getUpdateCount());
        assertTrue(!stmt.getMoreResults());
        assertEquals(-1, stmt.getUpdateCount());

        con.setAutoCommit(false);
        // This is where we lock the first line in the table
        stmt.executeUpdate("update ##SAfe0002 set val='Updated Line' where id=1");

        Statement stmt2 = con2.createStatement();
        stmt2.setQueryTimeout(1);

        try {
            stmt2.executeQuery("select * from ##SAfe0002");
            fail("Expecting timeout exception");
        } catch( SQLException ex ) {
            assertEquals(
                    "Expecting timeout exception. Got " + ex.getMessage(),
                    "HYT00", ex.getSQLState());
        }

        // SAfe What should we do with the results if the execution timed out?!

        con.commit();
        con.setAutoCommit(true);

        stmt.execute("drop table ##SAfe0002");
        stmt.close();

        // Just run a tiny query to make sure the stream is still in working
        // condition.
        ResultSet rs = stmt2.executeQuery("select 1");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertTrue(!rs.next());
        stmt2.close();
        con2.close();
    }

    // MT-unsafe!!!
    volatile int started, done;
    volatile boolean failed;

    /**
     * Test <code>CursorResultSet</code> concurrency. Create a number of threads that execute concurrent queries using
     * scrollable result sets. All requests should be run on the same connection (<code>Tds</code> instance).
     */
    public void testCursorResultSetConcurrency0003() throws Exception {
        Statement stmt0 = con.createStatement();
        stmt0.execute("create table #SAfe0003(id int primary key, val varchar(20) null) "+
            "insert into #SAfe0003 values (1, 'Line 1') "+
            "insert into #SAfe0003 values (2, 'Line 2')");
        while (stmt0.getMoreResults() || stmt0.getUpdateCount() != -1);

        final Object o1=new Object(), o2=new Object();

        int threadCount = 25;
        Thread threads[] = new Thread[threadCount];

        started = done = 0;
        failed = false;

        for (int i=0; i<threadCount; i++) {

            threads[i] = new Thread() {
                public void run() {
                    ResultSet rs;
                    Statement stmt = null;

                    try {
                        stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        rs = stmt.executeQuery("SELECT * FROM #SAfe0003");

                        assertEquals(null, rs.getWarnings());
                        assertEquals(null, stmt.getWarnings());

                        // Synchronize all threads
                        synchronized (o2) {
                            synchronized(o1) {
                                started++;
                                o1.notify();
                            }

                            try {
                                o2.wait();
                            } catch (InterruptedException e) {
                            }
                        }

                        assertNotNull("executeQuery should not return null", rs);
                        assertTrue(rs.next());
                        assertTrue(rs.next());
                        assertTrue(!rs.next());
                        assertTrue(rs.previous());
                        assertTrue(rs.previous());
                        assertTrue(!rs.previous());
                    } catch (SQLException e) {
                        e.printStackTrace();

                        synchronized (o1) {
                            failed = true;
                        }

                        fail("An SQL Exception occured: "+e);
                    } finally {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (SQLException e) {
                            }
                        }

                        // Notify that we're done
                        synchronized (o1) {
                            done++;
                            o1.notify();
                        }
                    }
                }
            };

            threads[i].start();
        }

        while (true) {
            synchronized (o1) {
                if (started == threadCount) {
                    break;
                }

                o1.wait();
            }
        }

        synchronized (o2) {
            o2.notifyAll();
        }

        boolean passed = true;

        for (int i = 0; i < threadCount; i++) {
            stmt0 = con.createStatement();
            ResultSet rs = stmt0.executeQuery("SELECT 1234");
            passed &= rs.next();
            passed &= !rs.next();
            stmt0.close();
        }

        while (true) {
            synchronized (o1) {
                if (done == threadCount) {
                    break;
                }

                o1.wait();
            }
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }

        stmt0.close();

        assertTrue(passed);
        assertTrue(!failed);
    }

    /**
     * Check that meta data information is fetched even for empty cursor-based result sets (bug #613199).
     *
     * @throws Exception
     */
    public void testCursorResultSetEmpty0004() throws Exception {
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = stmt.executeQuery("SELECT 5 Value WHERE 1=0");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());
        assertEquals("Value", rs.getMetaData().getColumnName(1));
        assertTrue(!rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.next();
        assertTrue(!rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.close();
        stmt.close();
    }

    /**
     * Check that the <code>isBeforeFirst</code>, <code>isAfterLast</code>,
     * <code>isFirst</code> and <code>isLast</code> methods work for
     * forward-only, read-only result sets (bug [1039876] MS SQL
     * JtdsResultSet.isAfterLast() always returns false).
     *
     * @throws Exception if an error condition occurs
     */
    public void testPlainResultSetPosition0004() throws Exception {
        Statement stmt = con.createStatement();

        // Try with an empty ResultSet
        ResultSet rs = stmt.executeQuery("SELECT 5 Value WHERE 1=0");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());
        assertEquals("Value", rs.getMetaData().getColumnName(1));
        assertTrue(!rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.next();
        assertTrue(!rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.close();

        // Try with a non-empty ResultSet
        rs = stmt.executeQuery("SELECT 5 Value");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());
        assertEquals("Value", rs.getMetaData().getColumnName(1));
        assertTrue(rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.next();
        assertTrue(!rs.isBeforeFirst());
        assertTrue(!rs.isAfterLast());
        assertTrue(rs.isFirst());
        assertTrue(rs.isLast());
        rs.next();
        assertTrue(!rs.isBeforeFirst());
        assertTrue(rs.isAfterLast());
        assertTrue(!rs.isFirst());
        assertTrue(!rs.isLast());
        rs.close();

        stmt.close();
    }

    /**
     * Check that values returned from bit fields are correct (not just 0) (bug #841670).
     *
     * @throws Exception
     */
    public void testBitFields0005() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("create table #SAfe0005(id int primary key, bit1 bit not null, bit2 bit not null) "+
            "insert into #SAfe0005 values (0, 0, 0) "+
            "insert into #SAfe0005 values (1, 1, 1) "+
            "insert into #SAfe0005 values (2, 0, 0)");
        while (stmt.getMoreResults() || stmt.getUpdateCount() != -1);

        ResultSet rs = stmt.executeQuery("SELECT * FROM #SAfe0005");
        while (rs.next()) {
            int id = rs.getInt(1);
            int bit1 = rs.getInt(2);
            int bit2 = rs.getInt(3);

            assertTrue("id: " + id + "; bit1: " + bit1 + "; bit2: " + bit2,
                       bit1 == id % 2 && (bit2 == id || id == 2 && bit2 == 0));
        }

        rs.close();
        stmt.close();
    }

    /**
     * Test that <code>CallableStatement</code>s with return values work correctly.
     *
     * @throws Exception
     */
    public void testCallableStatement0006() throws Exception {
        final int myVal = 13;

        Statement stmt = con.createStatement();
        stmt.execute("CREATE PROCEDURE #SAfe0006 @p1 INT, @p2 VARCHAR(20) OUT AS "
                     + "SELECT @p2=CONVERT(VARCHAR(20), @p1-1) "
                     + "SELECT @p1 AS value "
                     + "RETURN @p1+1");
        stmt.close();

        // Try all formats: escaped, w/ exec and w/o exec
        String[] sql = {"{?=call #SAfe0006(?,?)}", "exec ?=#SAfe0006 ?,?", "?=#SAfe0006 ?,?"};

        for (int i = 0; i < sql.length; i++) {
            // Execute it using executeQuery
            CallableStatement cs = con.prepareCall(sql[i]);
            cs.registerOutParameter(1, Types.INTEGER);
            cs.setInt(2, myVal);
            cs.registerOutParameter(3, Types.VARCHAR);
            cs.executeQuery().close();

            assertFalse(cs.getMoreResults());
            assertEquals(-1, cs.getUpdateCount());

            assertEquals(myVal+1, cs.getInt(1));
            assertEquals(String.valueOf(myVal-1), cs.getString(3));

            cs.close();

            // Now use execute
            cs = con.prepareCall(sql[i]);
            cs.registerOutParameter(1, Types.INTEGER);
            cs.setInt(2, myVal);
            cs.registerOutParameter(3, Types.VARCHAR);
            assertTrue(cs.execute());
            cs.getResultSet().close();

            assertFalse(cs.getMoreResults());
            assertEquals(-1, cs.getUpdateCount());

            assertEquals(myVal+1, cs.getInt(1));
            assertEquals(String.valueOf(myVal-1), cs.getString(3));

            cs.close();
        }
    }

    /**
     * Helper method for <code>testBigDecimal0007</code>. Inserts a BigDecimal
     * value obtained from a double value.
     *
     * @param stmt      <code>PreparedStatement</code> instance
     * @param val       the <code>double</code> value to insert
     * @param scaleFlag if <code>true</code> scale the value to 4, otherwise
     *                  leave it as it is
     */
    private static void insertBigDecimal(PreparedStatement stmt, double val,
                                         boolean scaleFlag)
            throws Exception {
        BigDecimal bd = new BigDecimal(val);

        if (scaleFlag) {
            bd = bd.setScale(4,
                    BigDecimal.ROUND_HALF_EVEN);
        }

        stmt.setBigDecimal(1, bd);
        stmt.execute();

        int rowCount = stmt.getUpdateCount();

        assertEquals(1, rowCount);

        assertTrue(stmt.getMoreResults());
        ResultSet rs = stmt.getResultSet();
        assertTrue(rs.next());

        assertEquals("Values don't match.", val, rs.getDouble(1), 0);
    }

    /**
     * Test <code>BigDecimal</code>s created from double values (i.e with very
     * large scales).
     */
    public void testBigDecimal0007() throws Exception {
        Statement createStmt = con.createStatement();
        createStmt.execute("CREATE TABLE #SAfe0007(value MONEY)");
        createStmt.close();

        PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO #SAfe0007(value) VALUES (?) "
                + "SELECT * FROM #SAfe0007 DELETE #SAfe0007");
        // Now test with certain values.
        insertBigDecimal(stmt, 1.1, false);
        insertBigDecimal(stmt, 0.1, false);
        insertBigDecimal(stmt, 0.1, true);
        insertBigDecimal(stmt, 0.01, false);
        insertBigDecimal(stmt, 0.01, true);
        insertBigDecimal(stmt, 0.02, false);
        insertBigDecimal(stmt, 0.02, true);
        insertBigDecimal(stmt, 0.25, false);

        stmt.close();
    }

    /**
     * Test writing <code>long</code> values to VARCHAR fields. There was a
     * regression introduced in release 0.6 that caused <code>long</code>
     * fields to be sent with non-zero scale and appear with decimals when
     * written into VARCHAR fields.
     */
    public void testLongToVarchar0008() throws Exception {
        long myVal = 13;

        Statement createStmt = con.createStatement();
        createStmt.execute("CREATE TABLE #SAfe0008(value VARCHAR(255))");
        createStmt.close();

        PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO #SAfe0008(value) values (CONVERT(VARCHAR(255), ?)) "
                + "SELECT * FROM #SAfe0008 DELETE #SAfe0008");

        stmt.setLong(1, myVal);
        stmt.execute();
        int rowCount = stmt.getUpdateCount();
        assertEquals(1, rowCount);

        assertTrue(stmt.getMoreResults());
        ResultSet rs = stmt.getResultSet();
        assertTrue(rs.next());

        assertEquals("Values don't match.",
                     String.valueOf(myVal), rs.getString(1));

        stmt.close();
    }

    /**
     * Test <code>ResultSet.deleteRow()</code> on updateable result sets.
     */
    public void testDeleteRow0009() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #SAfe0009(value VARCHAR(255) PRIMARY KEY)");
        stmt.close();

        PreparedStatement insStmt = con.prepareStatement(
                "INSERT INTO #SAfe0009(value) values (?)");
        insStmt.setString(1, "Row 1");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.setString(1, "Row 2");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.close();

        stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                                   ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery("SELECT * FROM #SAfe0009 ORDER BY 1");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());
        assertTrue(rs.last());
        assertTrue(!rs.rowDeleted());
        rs.deleteRow();
        assertTrue(rs.rowDeleted());
        rs.close();

        rs = stmt.executeQuery("SELECT * FROM #SAfe0009");
        assertTrue(rs.next());
        assertEquals("Row 1", rs.getString(1));
        assertTrue(!rs.next());
        rs.close();
        stmt.close();
    }

    /**
     * Test VARCHAR output parameters returned by CallableStatements.
     * <p>
     * An issue existed, caused by the fact that the parameter was sent to SQL
     * Server as a short VARCHAR (not XORed with 0x80) limiting its length to
     * 255 characters. See bug [815348] for more details.
     */
    public void testCallableStatementVarchar0010() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE PROCEDURE #SAfe0010 @p1 VARCHAR(255) OUT AS "
                     + "SELECT @p1 = @p1 + @p1 "
                     + "SELECT @p1 = @p1 + @p1 "
                     + "SELECT @p1 = @p1 + @p1 "
                     + "SELECT @p1 AS value "
                     + "RETURN 255");
        stmt.close();

        // 256 characters long string
        String myVal = "01234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789"
                + "01234";

        // Execute it using executeQuery
        CallableStatement cs = con.prepareCall("{?=call #SAfe0010(?)}");
        cs.registerOutParameter(1, Types.INTEGER);
        cs.setString(2, myVal);
        cs.registerOutParameter(2, Types.VARCHAR);
        ResultSet rs = cs.executeQuery();
        assertTrue(rs.next());
        String rsVal = rs.getString(1);
        rs.close();

        assertFalse(cs.getMoreResults());
        assertEquals(-1, cs.getUpdateCount());

        assertEquals(myVal.length(), cs.getInt(1));
        assertEquals(rsVal, cs.getString(2));

        cs.close();
    }

    /**
     * Test <code>ResultSet.updateRow()</code> on updateable result sets.
     */
    public void testUpdateRow0011() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #SAfe0011(value VARCHAR(255) PRIMARY KEY)");
        stmt.close();

        PreparedStatement insStmt = con.prepareStatement(
                "INSERT INTO #SAfe0011(value) values (?)");
        insStmt.setString(1, "Row 1");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.setString(1, "Row 2");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.close();

        stmt = con.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                   ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery("SELECT * FROM #SAfe0011 ORDER BY 1");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());
        assertTrue(rs.next());
        assertTrue(rs.next());
        rs.updateString(1, "Row X");
        rs.updateRow();
        rs.next();
        assertEquals("Row X", rs.getString(1));
        rs.close();
        stmt.close();
    }

    /**
     * Test <code>ResultSet.insertRow()</code> on updateable result sets.
     */
    public void testInsertRow0012() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #SAfe0012(value VARCHAR(255) PRIMARY KEY)");
        stmt.close();

        PreparedStatement insStmt = con.prepareStatement(
                "INSERT INTO #SAfe0012(value) values (?)");
        insStmt.setString(1, "Row 1");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.setString(1, "Row 2");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.close();

        stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                                   ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery("SELECT * FROM #SAfe0012 ORDER BY 1");
        assertEquals(null, stmt.getWarnings());
        assertEquals(null, rs.getWarnings());

        // Insert the new row
        rs.moveToInsertRow();
        rs.updateString(1, "Row X");
        rs.insertRow();

        // Check the ResultSet contents
        rs.moveToCurrentRow();
        rs.next();
        assertEquals("Row 1", rs.getString(1));
        rs.next();
        assertEquals("Row 2", rs.getString(1));
        rs.next();
        assertEquals("Row X", rs.getString(1));
        rs.close();
        stmt.close();
    }

    /**
     * Test how an "out-of-order" close behaves (e.g close the
     * <code>Connection</code> first, then the <code>Statement</code> anf
     * finally the <code>ResultSet</code>).
     */
    public void testOutOfOrderClose0013() throws Exception {
        Connection localConn = getConnection();
        Statement stmt = localConn.createStatement();
        stmt.execute("CREATE TABLE #SAfe0013(value VARCHAR(255) PRIMARY KEY)");

        PreparedStatement insStmt = localConn.prepareStatement(
                "INSERT INTO #SAfe0013(value) values (?)");
        insStmt.setString(1, "Row 1");
        assertEquals(1, insStmt.executeUpdate());
        insStmt.setString(1, "Row 2");
        assertEquals(1, insStmt.executeUpdate());

        ResultSet rs = stmt.executeQuery("SELECT * FROM #SAfe0013");

        // Close the connection first
        localConn.close();

        // Now, close the statements
        stmt.close();
        insStmt.close();

        // And finally, close the ResultSet
        rs.close();
    }

    /**
     * Test cursor-based <code>ResultSet</code>s obtained from
     * <code>PreparedStatement</code>s and <code>CallableStatement</code>s.
     */
    public void testPreparedAndCallableCursors0014() throws Exception {
//        Logger.setActive(true);
        Statement stmt = con.createStatement();
        stmt.executeUpdate("CREATE TABLE #SAfe0014(id INT PRIMARY KEY)");
        stmt.executeUpdate("INSERT INTO #SAfe0014 VALUES (1)");
        stmt.executeUpdate("CREATE PROCEDURE #sp_SAfe0014(@P1 INT, @P2 INT) AS "
                           + "SELECT id, @P2 FROM #SAfe0014 WHERE id=@P1");
        stmt.close();

        PreparedStatement ps = con.prepareStatement("SELECT id FROM #SAfe0014",
                                                    ResultSet.TYPE_SCROLL_SENSITIVE,
                                                    ResultSet.CONCUR_UPDATABLE);
        ResultSet resultSet = ps.executeQuery();
        // No warnings
        assertEquals(null, resultSet.getWarnings());
        assertEquals(null, ps.getWarnings());
        // Correct ResultSet
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
        assertTrue(!resultSet.next());
        // Correct meta data
        ResultSetMetaData rsmd = resultSet.getMetaData();
        assertEquals("id", rsmd.getColumnName(1));
        assertEquals("#SAfe0014", rsmd.getTableName(1));
        // Insert row
        resultSet.moveToInsertRow();
        resultSet.updateInt(1, 2);
        resultSet.insertRow();
        resultSet.moveToCurrentRow();
        // Check correct row count
        resultSet.last();
        assertEquals(2, resultSet.getRow());
        resultSet.close();
        ps.close();

        ps = con.prepareStatement("SELECT id, ? FROM #SAfe0014 WHERE id = ?",
                                  ResultSet.TYPE_SCROLL_SENSITIVE,
                                  ResultSet.CONCUR_UPDATABLE);
        ps.setInt(1, 5);
        ps.setInt(2, 1);
        resultSet = ps.executeQuery();
        // No warnings
        assertEquals(null, resultSet.getWarnings());
        assertEquals(null, ps.getWarnings());
        // Correct ResultSet
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
        assertEquals(5, resultSet.getInt(2));
        assertTrue(!resultSet.next());
        // Correct meta data
        rsmd = resultSet.getMetaData();
        assertEquals("id", rsmd.getColumnName(1));
        assertEquals("#SAfe0014", rsmd.getTableName(1));
        resultSet.close();
        ps.close();

        CallableStatement cs = con.prepareCall("{call #sp_SAfe0014(?,?)}",
                                               ResultSet.TYPE_SCROLL_SENSITIVE,
                                               ResultSet.CONCUR_UPDATABLE);
        cs.setInt(1, 1);
        cs.setInt(2, 3);
        resultSet = cs.executeQuery();
        // No warnings
        assertEquals(null, resultSet.getWarnings());
        assertEquals(null, cs.getWarnings());
        // Correct ResultSet
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
        assertEquals(3, resultSet.getInt(2));
        assertTrue(!resultSet.next());
        // Correct meta data
        rsmd = resultSet.getMetaData();
        assertEquals("id", rsmd.getColumnName(1));
        assertEquals("#SAfe0014", rsmd.getTableName(1));
        resultSet.close();
        cs.close();
    }

    /**
     * Test batch updates for both plain and prepared statements.
     */
    public void testBatchUpdates0015() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #SAfe0015(value VARCHAR(255) PRIMARY KEY)");

        // Execute prepared batch
        PreparedStatement insStmt = con.prepareStatement(
                "INSERT INTO #SAfe0015(value) values (?)");
        insStmt.setString(1, "Row 1");
        insStmt.addBatch();
        insStmt.setString(1, "Row 2");
        insStmt.addBatch();
        int[] res = insStmt.executeBatch();
        assertEquals(2, res.length);
        assertEquals(1, res[0]);
        assertEquals(1, res[1]);

        // Execute an empty batch
        res = insStmt.executeBatch();
        insStmt.close();
        assertEquals(0, res.length);

        // Execute plain batch
        stmt.addBatch("UPDATE #SAfe0015 SET value='R1' WHERE value='Row 1'");
        stmt.addBatch("UPDATE #SAfe0015 SET value='R2' WHERE value='Row 2'");
        res = stmt.executeBatch();
        assertEquals(2, res.length);
        assertEquals(1, res[0]);
        assertEquals(1, res[1]);

        // Execute an empty batch
        res = stmt.executeBatch();
        assertEquals(0, res.length);

        // Close the statement
        stmt.close();
    }

    /**
     * Test that dates prior to 06/15/1940 0:00:00 are stored and retrieved
     * correctly.
     */
    public void testOldDates0016() throws Exception {
        Statement stmt = con.createStatement();
        stmt.execute("CREATE TABLE #SAfe0016(id INT, value DATETIME)");

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String[] dates = {
            "1983-10-30 02:00:00",
            "1983-10-30 01:59:59",
            "1940-06-14 23:59:59",
            "1911-03-11 00:51:39",
            "1911-03-11 00:51:38",
            "1900-01-01 01:00:00",
            "1900-01-01 00:59:59",
            "1900-01-01 00:09:21",
            "1900-01-01 00:09:20",
            "1753-01-01 00:00:00"
        };

        // Insert the timestamps
        PreparedStatement pstmt =
                con.prepareStatement("INSERT INTO #SAfe0016 VALUES(?, ?)");

        for (int i = 0; i < dates.length; i++) {
            pstmt.setInt(1, i);
            pstmt.setString(2, dates[i]);
            pstmt.addBatch();
        }

        int[] res = pstmt.executeBatch();
        // Check that the insertion went ok

        assertEquals(dates.length, res.length);

        for (int i = 0; i < dates.length; i++) {
            assertEquals(1, res[i]);
        }

        // Select the timestamps and make sure they are the same
        ResultSet rs = stmt.executeQuery(
                "SELECT value FROM #SAfe0016 ORDER BY id");

        int counter = 0;

        while (rs.next()) {
            assertEquals(format.parse(dates[counter]), rs.getTimestamp(1));
            ++counter;
        }

        // Close everything
        rs.close();
        stmt.close();
        pstmt.close();
    }

    /**
     * Test bug #926620 - Too long value for VARCHAR field.
     */
/* does not work with SQL 6.5
    public void testCursorLargeCharInsert0017() throws Exception {
        Statement stmt = con.createStatement(
                ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        stmt.execute("CREATE TABLE #SAfe0017(value VARCHAR(10) PRIMARY KEY)");

        // Create the updateable ResultSet
        ResultSet rs = stmt.executeQuery(
                "SELECT value FROM #SAfe0017");

        // Try inserting a character string less than 10 characters long
        rs.moveToInsertRow();
        rs.updateString(1, "Test");
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.last();
        // Check that we do indeed have one row in the ResultSet now
        assertEquals(1, rs.getRow());

        // Try inserting a character string more than 10 characters long
        rs.moveToInsertRow();
        rs.updateString(1, "Testing: 1, 2, 3...");
        try {
            rs.insertRow();
            fail("Should cause an SQLException with native error number 8152"
                 + "and SQL state 22001");
        } catch (SQLException ex) {
//          assertEquals("22001", ex.getSQLState());
            assertTrue(ex instanceof DataTruncation);
        }

        // Close everything
        rs.close();
        stmt.close();
    }*/

    /**
     * Test for bug [939206] TdsException: can't sent this BigDecimal
     */
    public void testBigDecimal1() throws Exception {
        Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT @@MAX_PRECISION");
        assertTrue(rs.next());
        int maxPrecision = rs.getInt(1);
        rs.close();
        BigDecimal maxval = new BigDecimal("1E+" + maxPrecision);
        maxval = maxval.subtract(new BigDecimal(1));

        // maxval now = 99999999999999999999999999999999999999
        if (maxPrecision > 28) {
            stmt.execute("create table #testBigDecimal1 (id int primary key, data01 decimal(38,0), data02 decimal(38,12) null, data03 money)");
        } else {
            stmt.execute("create table #testBigDecimal1 (id int primary key, data01 decimal(28,0), data02 decimal(28,12) null, data03 money)");
        }

        PreparedStatement pstmt = con.prepareStatement("insert into #testBigDecimal1 (id, data01, data02, data03) values (?,?,?,?)");
        pstmt.setInt(1, 1);

        try {
            pstmt.setBigDecimal(2, maxval.add(new BigDecimal(1)));
            assertTrue(false); // Should fail
        } catch (SQLException e) {
            // System.out.println(e.getMessage());
            // OK Genuinely can't send this one!
        }

        pstmt.setBigDecimal(2, maxval);
        pstmt.setBigDecimal(3, new BigDecimal(1.0 / 3.0)); // Scale > 38
        pstmt.setBigDecimal(4, new BigDecimal("12345.56789"));
        assertTrue(pstmt.executeUpdate() == 1);
        pstmt.close();
        rs = stmt.executeQuery("SELECT * FROM #testBigDecimal1");
        assertTrue(rs.next());
        assertEquals(maxval, rs.getBigDecimal(2));
        assertEquals(new BigDecimal("0.333333333333"), rs.getBigDecimal(3)); // Rounded to scale 10
        assertEquals(new BigDecimal("12345.5679"), rs.getBigDecimal(4)); // Money has scale of 4
        rs.close();
        maxval = maxval.negate();
        Statement stmt2 = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        rs = stmt2.executeQuery("SELECT * FROM #testBigDecimal1");
        SQLWarning warn = stmt.getWarnings();

        while (warn != null) {
            System.out.println(warn.getMessage());
            warn = warn.getNextWarning();
        }

        assertTrue(rs.next());
        rs.updateBigDecimal("data01", maxval);
        rs.updateNull("data02");
        rs.updateObject("data03", new BigDecimal("-12345.56789"), 2); // Round to scale 2
        rs.updateRow();
        rs.close();
        stmt2.close();
        rs = stmt.executeQuery("SELECT * FROM #testBigDecimal1");
        assertTrue(rs.next());
        assertEquals(maxval, rs.getBigDecimal(2));
        assertEquals(null, rs.getBigDecimal(3));
        assertEquals(new BigDecimal("-12345.5700"), rs.getBigDecimal(4));
        rs.close();
        stmt.close();
    }

    /**
     * Test for bug [963799] float values change when written to the database
     */
    public void testFloat1() throws Exception {
        float value = 2.2f;

        Statement stmt = con.createStatement();
        stmt.execute("create table #testFloat1 (data decimal(28,10))");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("insert into #testFloat1 (data) values (?)");
        pstmt.setFloat(1, value);
        assertTrue(pstmt.executeUpdate() == 1);
        pstmt.close();

        pstmt = con.prepareStatement("select data from #testFloat1");
        ResultSet rs = pstmt.executeQuery();

        assertTrue(rs.next());
        assertTrue(value == rs.getFloat(1));
        assertTrue(!rs.next());

        pstmt.close();
        rs.close();
    }

    /**
     * Test for bug [983561] getDatetimeValue truncates fractional milliseconds
     */
    public void testDatetimeRounding1() throws Exception {
    	long dateTime = 1089297738677L;
    	Timestamp value = new Timestamp(dateTime);

        Statement stmt = con.createStatement();
        stmt.execute("create table #dtr1 (data datetime)");
        stmt.close();

        PreparedStatement pstmt = con.prepareStatement("insert into #dtr1 (data) values (?)");
        pstmt.setTimestamp(1, value);
        assertTrue(pstmt.executeUpdate() == 1);
        pstmt.close();

        pstmt = con.prepareStatement("select data from #dtr1");
        ResultSet rs = pstmt.executeQuery();

        assertTrue(rs.next());
        assertTrue(value.equals(rs.getTimestamp(1)));
        assertTrue(!rs.next());

        pstmt.close();
        rs.close();
    }
}
