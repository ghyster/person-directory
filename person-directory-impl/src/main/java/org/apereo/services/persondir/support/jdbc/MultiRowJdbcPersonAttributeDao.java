/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apereo.services.persondir.support.jdbc;

import com.google.common.collect.MapMaker;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributes;
import org.apereo.services.persondir.support.MultivaluedPersonAttributeUtils;
import org.apereo.services.persondir.support.NamedPersonImpl;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link IPersonAttributeDao}
 * implementation that maps attribute names and values from name and value column
 * pairs. This is usefull if user attributes are stored in a table like:<br>
 * <table border="1">
 *  <tr>
 *      <th>USER_NM</th>
 *      <th>ATTR_NM</th>
 *      <th>ATTR_VL</th>
 *  </tr>
 *  <tr>
 *      <td>jstudent</td>
 *      <td>name.given</td>
 *      <td>joe</td>
 *  </tr>
 *  <tr>
 *      <td>jstudent</td>
 *      <td>name.family</td>
 *      <td>student</td>
 *  </tr>
 *  <tr>
 *      <td>badvisor</td>
 *      <td>name.given</td>
 *      <td>bob</td>
 *  </tr>
 *  <tr>
 *      <td>badvisor</td>
 *      <td>name.family</td>
 *      <td>advisor</td>
 *  </tr>
 * </table>
 *
 * <br>
 *
 * This class expects 1 to N row results for a query, with each row containing 1 to N name
 * value attribute mappings and the userName of the user the attributes are for. This contrasts
 * {@link SingleRowJdbcPersonAttributeDao} which expects
 * a single row result for a user query. <br>
 *
 * <br>
 * <br>
 * Configuration:
 * <table border="1">
 *     <tr>
 *         <th>Property</th>
 *         <th>Description</th>
 *         <th>Required</th>
 *         <th>Default</th>
 *     </tr>
 *     <tr>
 *         <td  valign="top">nameValueColumnMappings</td>
 *         <td>
 *             A {@link Map} of attribute name columns to attribute value columns. A single result row can have multiple
 *             name columns and multiple value columns associated with each name. The values of the {@link Map} can be
 *             either {@link String} or {@link Collection} of String.
 *         </td>
 *         <td valign="top">Yes</td>
 *         <td valign="top">null</td>
 *     </tr>
 * </table>
 *
 * @author andrew.petro@yale.edu
 * @author Eric Dalquist <a href="mailto:edalquist@unicon.net">edalquist@unicon.net</a>

 * @since uPortal 2.5
 */
public class MultiRowJdbcPersonAttributeDao extends AbstractJdbcPersonAttributeDao<Map<String, Object>> {
    private static final RowMapper<Map<String, Object>> MAPPER = new ColumnMapParameterizedRowMapper();

    /**
     * {@link Map} of columns from a name column to value columns.
     * Keys are Strings, Values are Strings or List of Strings 
     */
    private Map<String, Set<String>> nameValueColumnMappings = null;

    public MultiRowJdbcPersonAttributeDao() {
        super();
    }

    /**
     * Creates a new MultiRowJdbcPersonAttributeDao specifying the DataSource and SQL to use.
     *
     * @param ds The DataSource to get connections from for executing queries, may not be null.
     * @param sql The SQL to execute for user attributes, may not be null.
     */
    public MultiRowJdbcPersonAttributeDao(final DataSource ds, final String sql) {
        super(ds, sql);
    }


    /**
     * @return The Map of name column to value column(s). 
     */
    public Map<String, Set<String>> getNameValueColumnMappings() {
        return this.nameValueColumnMappings;
    }

    /**
     * The {@link Map} of columns from a name column to value columns. Keys are Strings,
     * Values are Strings or {@link java.util.List} of Strings.
     *
     * @param nameValueColumnMap The Map of name column to value column(s). 
     */
    public void setNameValueColumnMappings(final Map<String, ?> nameValueColumnMap) {
        if (nameValueColumnMap == null) {
            this.nameValueColumnMappings = null;
        } else {
            var mappings = MultivaluedPersonAttributeUtils.parseAttributeToAttributeMapping(nameValueColumnMap);

            if (mappings.containsValue(null)) {
                throw new IllegalArgumentException("nameValueColumnMap may not have null values");
            }
            
            this.nameValueColumnMappings = mappings;
        }
    }


    /* (non-Javadoc)
     * @see org.jasig.services.persondir.support.jdbc.AbstractJdbcPersonAttributeDao#getRowMapper()
     */
    @Override
    protected RowMapper<Map<String, Object>> getRowMapper() {
        return MAPPER;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<IPersonAttributes> parseAttributeMapFromResults(final List<Map<String, Object>> queryResults, final String queryUserName) {
        final Map<String, Map<String, List<Object>>> peopleAttributesBuilder = new MapMaker().makeMap();

        var userNameAttribute = this.getConfiguredUserNameAttribute();

        for (var queryResult : queryResults) {
            final String userName;  // Choose a username from the best available option
            if (this.isUserNameAttributeConfigured() && queryResult.containsKey(userNameAttribute)) {
                // Option #1:  An attribute is named explicitly in the config, 
                // and that attribute is present in the results from LDAP;  use it
                var userNameValue = queryResult.get(userNameAttribute);
                userName = userNameValue.toString();
            } else if (queryUserName != null) {
                // Option #2:  Use the userName attribute provided in the query 
                // parameters.  (NB:  I'm not entirely sure this choice is 
                // preferable to Option #3.  Keeping it because it most closely 
                // matches the legacy behavior there the new option -- Option #1 
                // -- doesn't apply.  ~drewwills)
                userName = queryUserName;
            } else if (queryResult.containsKey(userNameAttribute)) {
                // Option #3:  Create the IPersonAttributes useing the default 
                // userName attribute, which we know to be present
                var userNameValue = queryResult.get(userNameAttribute);
                userName = userNameValue.toString();
            } else {
                throw new BadSqlGrammarException("No userName column named '" + userNameAttribute + "' exists in result set and no userName provided in query Map", this.getQueryTemplate(), null);
            }

            var attributes = peopleAttributesBuilder.computeIfAbsent(userName,
                    key -> new LinkedHashMap<String, List<Object>>());

            //Iterate over each attribute column mapping to get the data from the row
            for (var columnMapping : this.nameValueColumnMappings.entrySet()) {
                var keyColumn = columnMapping.getKey();

                //Get the attribute name for the specified column
                var attrNameObj = queryResult.get(keyColumn);
                if (attrNameObj == null && !queryResult.containsKey(keyColumn)) {
                    throw new BadSqlGrammarException("No attribute key column named '" + keyColumn + "' exists in result set", this.getQueryTemplate(), null);
                }
                var attrName = String.valueOf(attrNameObj);

                //Get the columns containing the values and add all values to a List
                var valueColumns = columnMapping.getValue();
                final List<Object> attrValues = new ArrayList<>(valueColumns.size());
                for (var valueColumn : valueColumns) {
                    var attrValue = queryResult.get(valueColumn);
                    if (attrValue == null && !queryResult.containsKey(valueColumn)) {
                        throw new BadSqlGrammarException("No attribute value column named '" + valueColumn + "' exists in result set", this.getQueryTemplate(), null);
                    }

                    attrValues.add(attrValue);
                }

                //Add the name/values to the attributes Map
                MultivaluedPersonAttributeUtils.addResult(attributes, attrName, attrValues);
            }
        }


        //Convert the builder structure into a List of IPersons
        final List<IPersonAttributes> people = new ArrayList<>(peopleAttributesBuilder.size());

        for (var mappedAttributesEntry : peopleAttributesBuilder.entrySet()) {
            var userName = mappedAttributesEntry.getKey();
            var attributes = mappedAttributesEntry.getValue();
            // PERSONDIR-89, PERSONDIR-91 Should this be CaseInsensitiveNamedPersonImpl like SingleRowJdbcPersonAttribute?
            var person = new NamedPersonImpl(userName, attributes);
            people.add(person);
        }

        return people;
    }
}
