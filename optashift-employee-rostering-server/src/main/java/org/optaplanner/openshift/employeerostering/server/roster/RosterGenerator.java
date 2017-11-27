/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.openshift.employeerostering.server.roster;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.optaplanner.openshift.employeerostering.shared.employee.Employee;
import org.optaplanner.openshift.employeerostering.shared.employee.EmployeeAvailability;
import org.optaplanner.openshift.employeerostering.shared.employee.EmployeeAvailabilityState;
import org.optaplanner.openshift.employeerostering.shared.employee.EmployeeSkillProficiency;
import org.optaplanner.openshift.employeerostering.shared.roster.Roster;
import org.optaplanner.openshift.employeerostering.shared.shift.Shift;
import org.optaplanner.openshift.employeerostering.shared.skill.Skill;
import org.optaplanner.openshift.employeerostering.shared.spot.Spot;
import org.optaplanner.openshift.employeerostering.shared.tenant.Tenant;
import org.optaplanner.openshift.employeerostering.shared.timeslot.TimeSlot;
import org.optaplanner.openshift.employeerostering.shared.timeslot.TimeSlotState;
import org.optaplanner.openshift.employeerostering.server.common.generator.StringDataGenerator;

@Singleton
@Startup
public class RosterGenerator {

    private final StringDataGenerator tenantNameGenerator = StringDataGenerator.buildLocationNames();
    private final StringDataGenerator employeeNameGenerator = StringDataGenerator.buildFullNames();
    private final StringDataGenerator spotNameGenerator = StringDataGenerator.buildAssemblyLineNames();

    private final StringDataGenerator skillNameGenerator = new StringDataGenerator()
            .addPart(
                    "Mechanical",
                    "Electrical",
                    "Safety",
                    "Transportation",
                    "Operational",
                    "Physics",
                    "Monitoring",
                    "ICT")
            .addPart(
                    "bachelor",
                    "engineer",
                    "instructor",
                    "coordinator",
                    "manager",
                    "expert",
                    "inspector",
                    "analyst");

    private Random random = new Random(37);

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unused")
    public RosterGenerator() {}

    /**
     * For benchmark only
     * @param entityManager never null
     */
    public RosterGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @PostConstruct
    public void setUpGeneratedData() {
        tenantNameGenerator.predictMaximumSizeAndReset(10);
        generateRoster(10, 7, false);
        generateRoster(10, 28, false);
        generateRoster(20, 28, false);
        generateRoster(40, 28 * 2, false);
        generateRoster(80, 28 * 4, false);
        generateRoster(10, 28, true);
        generateRoster(20, 28, true);
        generateRoster(40, 28 * 2, true);
        generateRoster(80, 28 * 4, true);
    }

    @Transactional
    public Roster generateRoster(int spotListSize, int timeSlotListSize, boolean continuousPlanning) {
        int employeeListSize = spotListSize * 7 / 2;
        int skillListSize = (spotListSize + 4) / 5;
        Integer tenantId = createTenant(spotListSize, employeeListSize);
        List<Skill> skillList = createSkillList(tenantId, skillListSize);
        List<Spot> spotList = createSpotList(tenantId, spotListSize, skillList);
        List<TimeSlot> timeSlotList = createTimeSlotList(tenantId, timeSlotListSize, continuousPlanning);
        List<Employee> employeeList = createEmployeeList(tenantId, employeeListSize, skillList);
        List<EmployeeAvailability> employeeAvailabilityList = createEmployeeAvailabilityList(
                tenantId, employeeList, timeSlotList);
        List<Shift> shiftList = createShiftList(tenantId, spotList, timeSlotList, employeeList, continuousPlanning);
        return new Roster((long) tenantId, tenantId,
                skillList, spotList, employeeList, timeSlotList, employeeAvailabilityList, shiftList);
    }

    private Integer createTenant(int spotListSize, int employeeListSize) {
        Tenant tenant = new Tenant(tenantNameGenerator.generateNextValue()
                + " (" + employeeListSize +  " employees, " + spotListSize + "spots)");
        entityManager.persist(tenant);
        return tenant.getId();
    }

    private List<Skill> createSkillList(Integer tenantId, int size) {
        List<Skill> skillList = new ArrayList<>(size);
        skillNameGenerator.predictMaximumSizeAndReset(size);
        for (int i = 0; i < size; i++) {
            String name = skillNameGenerator.generateNextValue();
            Skill skill = new Skill(tenantId, name);
            entityManager.persist(skill);
            skillList.add(skill);
        }
        return skillList;
    }

    private List<Spot> createSpotList(Integer tenantId, int size, List<Skill> skillList) {
        List<Spot> spotList = new ArrayList<>(size);
        spotNameGenerator.predictMaximumSizeAndReset(size);
        for (int i = 0; i < size; i++) {
            String name = spotNameGenerator.generateNextValue();
            Spot spot = new Spot(tenantId, name, skillList.get(random.nextInt(skillList.size())));
            entityManager.persist(spot);
            spotList.add(spot);
        }
        return spotList;
    }

    private List<TimeSlot> createTimeSlotList(Integer tenantId, int size, boolean continuousPlanning) {
        List<TimeSlot> timeSlotList = new ArrayList<>(size);
        LocalDateTime previousEndDateTime = LocalDateTime.of(2017, 2, 1, 6, 0);
        for (int i = 0; i < size; i++) {
            LocalDateTime startDateTime = previousEndDateTime;
            LocalDateTime endDateTime = startDateTime.plusHours(8);
            TimeSlot timeSlot = new TimeSlot(tenantId, startDateTime, endDateTime);
            if (continuousPlanning && i < size / 2) {
                if (i < size / 4) {
                    timeSlot.setTimeSlotState(TimeSlotState.HISTORY);
                } else {
                    timeSlot.setTimeSlotState(TimeSlotState.TENTATIVE);
                }
            } else {
                timeSlot.setTimeSlotState(TimeSlotState.DRAFT);
            }
            entityManager.persist(timeSlot);
            timeSlotList.add(timeSlot);
            previousEndDateTime = endDateTime;
        }
        return timeSlotList;
    }

    private List<Employee> createEmployeeList(Integer tenantId, int size, List<Skill> generalSkillList) {
        List<Employee> employeeList = new ArrayList<>(size);
        employeeNameGenerator.predictMaximumSizeAndReset(size);
        for (int i = 0; i < size; i++) {
            String name = employeeNameGenerator.generateNextValue();
            Employee employee = new Employee(tenantId, name);
            employee.setSkillProficiencyList(
                    extractRandomSubList(generalSkillList, 1.0).stream()
                            .map(skill -> new EmployeeSkillProficiency(tenantId, employee, skill))
                            .collect(Collectors.toCollection(ArrayList::new)));
            entityManager.persist(employee);
            employeeList.add(employee);
        }
        return employeeList;
    }

    private List<EmployeeAvailability> createEmployeeAvailabilityList(Integer tenantId,
            List<Employee> employeeList, List<TimeSlot> timeSlotList) {
        List<EmployeeAvailability> employeeAvailabilityList = new ArrayList<>(employeeList.size() * timeSlotList.size());
        for (Employee employee : employeeList) {
            for (TimeSlot timeSlot : extractRandomSubList(timeSlotList, 0.6)) {
                EmployeeAvailability employeeAvailability = new EmployeeAvailability(tenantId, employee, timeSlot);
                employeeAvailability.setState(EmployeeAvailabilityState.values()[
                        random.nextInt(EmployeeAvailabilityState.values().length)]);
                entityManager.persist(employeeAvailability);
            }
        }
        return employeeAvailabilityList;
    }

    private List<Shift> createShiftList(Integer tenantId, List<Spot> spotList, List<TimeSlot> timeSlotList,
            List<Employee> employeeList, boolean continuousPlanning) {
        List<Shift> shiftList = new ArrayList<>(spotList.size() * timeSlotList.size());
        for (Spot spot : spotList) {
            boolean weekendEnabled = random.nextInt(10) < 8;
            boolean nightEnabled = weekendEnabled && random.nextInt(10) < 8;
            int timeSlotIndex = 0;
            for (TimeSlot timeSlot : timeSlotList) {
                DayOfWeek dayOfWeek = timeSlot.getStartDateTime().getDayOfWeek();
                if (!weekendEnabled && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                    timeSlotIndex++;
                    continue;
                }
                if (!nightEnabled && timeSlot.getStartDateTime().getHour() >= 20) {
                    timeSlotIndex++;
                    continue;
                }
                Shift shift = new Shift(tenantId, spot, timeSlot);
                if (continuousPlanning) {
                    if (timeSlotIndex < timeSlotList.size() / 2) {
                        List<Employee> availableEmployeeList = employeeList.stream()
//                                .filter(employee -> !employee.getUnavailableTimeSlotSet().contains(timeSlot))
                                .collect(Collectors.toList());
                        Employee employee = availableEmployeeList.get(random.nextInt(availableEmployeeList.size()));
                        shift.setEmployee(employee);
                        shift.setLockedByUser(random.nextDouble() < 0.05);
                    }
                }
                entityManager.persist(shift);
                shiftList.add(shift);
                timeSlotIndex++;
            }

        }
        return shiftList;

    }

    private <E> List<E> extractRandomSubList(List<E> list, double maxRelativeSize) {
        List<E> subList = new ArrayList<>(list);
        Collections.shuffle(subList, random);
        // TODO List.subList() doesn't allow outer list to be garbage collected
        return subList.subList(0, random.nextInt((int) (list.size() * maxRelativeSize)) + 1);
    }

}
