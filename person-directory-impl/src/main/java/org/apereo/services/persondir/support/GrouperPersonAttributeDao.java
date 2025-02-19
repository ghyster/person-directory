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
package org.apereo.services.persondir.support;

import edu.internet2.middleware.grouperClient.api.GcGetGroups;
import edu.internet2.middleware.grouperClient.ws.beans.WsGetGroupsResult;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroup;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import org.apereo.services.persondir.IPersonAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class implementing a minimal <code>IPersonAttributeDao</code> API only used by CAS which simply reads all
 * the groups from Grouper repository
 * for a given principal and adopts them to <code>IPersonAttributes</code> instance. 
 * All other unimplemented methods throw <code>UnsupportedOperationException</code>
 * <br>
 * This implementation uses Grouper's <i>grouperClient</i> library to query Grouper's back-end repository.
 * <br>
 *
 * Note: All the Grouper server connection configuration for grouperClient is defined in
 * <i>grouper.client.properties</i> file and must be available
 * in client application's (CAS web application) classpath.
 *
 * @author Dmitriy Kopylenko
 */
public class GrouperPersonAttributeDao extends BasePersonAttributeDao {
    private IUsernameAttributeProvider usernameAttributeProvider = new SimpleUsernameAttributeProvider();

    public static final String DEFAULT_GROUPER_ATTRIBUTES_KEY = "grouperGroups";

    private Map<String, String> parameters = new LinkedHashMap<>();

    private GrouperSubjectType subjectType = GrouperSubjectType.SUBJECT_ID;

    public IUsernameAttributeProvider getUsernameAttributeProvider() {
        return usernameAttributeProvider;
    }

    public GrouperSubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(final GrouperSubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(final Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void setUsernameAttributeProvider(final IUsernameAttributeProvider usernameAttributeProvider) {
        this.usernameAttributeProvider = usernameAttributeProvider;
    }

    @Override
    public IPersonAttributes getPerson(final String subjectId, final IPersonAttributeDaoFilter filter) {
        if (!this.isEnabled()) {
            return null;
        }
        Objects.requireNonNull(subjectId, "username cannot be null");

        var groupsClient = new GcGetGroups();
        switch (this.subjectType) {
            case SUBJECT_IDENTIFIER:
                groupsClient.addSubjectIdentifier(subjectId);
                break;
            case SUBJECT_ATTRIBUTE_NAME:
                groupsClient.addSubjectAttributeName(subjectId);
                break;
            case SUBJECT_ID:
            default:
                groupsClient.addSubjectId(subjectId);
                break;
        }
        
        parameters.forEach(groupsClient::addParam);
        final Map<String, List<Object>> grouperGroupsAsAttributesMap = new HashMap<>(1);
        final List<Object> groupsList = new ArrayList<>();
        grouperGroupsAsAttributesMap.put("grouperGroups", groupsList);
        var personAttributes = new AttributeNamedPersonImpl(grouperGroupsAsAttributesMap);

        //Now retrieve and populate the attributes (groups from Grouper)
        for (final WsGetGroupsResult groupsResult : groupsClient.execute().getResults()) {
            for (final WsGroup group : groupsResult.getWsGroups()) {
                groupsList.add(group.getName());
            }
        }
        return personAttributes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getPossibleUserAttributeNames(final IPersonAttributeDaoFilter filter) {
        return Collections.EMPTY_SET;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getAvailableQueryAttributes(final IPersonAttributeDaoFilter filter) {
        return Collections.EMPTY_SET;
    }

    @Override
    public Set<IPersonAttributes> getPeople(final Map<String, Object> query,
                                            final IPersonAttributeDaoFilter filter) {
        return getPeopleWithMultivaluedAttributes(MultivaluedPersonAttributeUtils.stuffAttributesIntoListValues(query, filter), filter);
    }

    @Override
    public Set<IPersonAttributes> getPeopleWithMultivaluedAttributes(final Map<String, List<Object>> query,
                                                                     final IPersonAttributeDaoFilter filter) {
        final Set<IPersonAttributes> people = new LinkedHashSet<>();
        var username = usernameAttributeProvider.getUsernameFromQuery(query);
        var person = getPerson(username, filter);
        if (person != null) {
            people.add(person);
        }
        return people;
    }

    public enum GrouperSubjectType {
        SUBJECT_ID,
        SUBJECT_IDENTIFIER,
        SUBJECT_ATTRIBUTE_NAME
    }
}
