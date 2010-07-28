package com.springsource.greenhouse.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventRepository implements EventRepository {

	private JdbcTemplate jdbcTemplate;

	@Inject
	public JdbcEventRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Event> findUpcomingEvents() {		
		return jdbcTemplate.query("select e.id, e.title, e.startDate, e.endDate, e.location, e.description, e.name, g.hashtag, g.name as groupName, g.profileKey as groupProfileKey from Event e inner join MemberGroup g on e.memberGroup = g.id where e.endDate > ? order by e.startDate", eventMapper, new Date());
	}
	
	public Event findEventByName(String group, Integer year, Integer month,String name) {
		return jdbcTemplate.queryForObject("select e.id, e.title, e.startDate, e.endDate, e.location, e.description, e.name, g.hashtag, g.name as groupName, g.profileKey as groupProfileKey from Event e inner join MemberGroup g on e.memberGroup = g.id where g.profileKey = ? and extract(year from e.startDate) = ? and extract(month from e.startDate) = ? and e.name = ?", eventMapper, group, year, month, name);
	}

	public String findEventHashtag(Long eventId) {
		return jdbcTemplate.queryForObject("select g.hashtag from Event e, MemberGroup g where e.id = ? and e.memberGroup = g.id", String.class, eventId);
	}

	public String findSessionHashtag(Long eventId, Short sessionCode) {
		return buildSessionHashtag(findEventHashtag(eventId), sessionCode);
	}

	public List<EventSession> findTodaysSessions(Long eventId) {
		LocalDate day = new LocalDate();
		Date startInstant = day.toDateTimeAtStartOfDay(DateTimeZone.UTC).toDate();
		Date endInstant = day.plusDays(1).toDateTimeAtStartOfDay(DateTimeZone.UTC).toDate();
		return jdbcTemplate.query("select s.code, s.title, s.startTime, s.endTime, s.description, m.firstName, m.lastName from EventSession s inner join EventSessionLeader l on s.code = l.session inner join Member m on l.leader = m.id where s.event = ? and s.startTime > ? and s.endTime < ?", eventSessionsExtractor, eventId, startInstant, endInstant);
	}

	// internal helpers
	
	private String buildSessionHashtag(String hashtag, Short sessionCode) {
		return hashtag + "-" + sessionCode;
	}
	
	private RowMapper<Event> eventMapper = new RowMapper<Event>() {
		public Event mapRow(ResultSet rs, int row) throws SQLException {
			return new Event(rs.getLong("id"), rs.getString("title"), new DateTime(rs.getTimestamp("startDate")), new DateTime(rs.getTimestamp("endDate")), rs.getString("location"), rs.getString("description"), rs.getString("name"),
					rs.getString("hashtag"), rs.getString("groupName"), rs.getString("groupProfileKey"));
		}
	};

	private ResultSetExtractor<List<EventSession>> eventSessionsExtractor = new ResultSetExtractor<List<EventSession>>() {
		public List<EventSession> extractData(ResultSet rs) throws SQLException, DataAccessException {
			List<EventSession> sessions = new ArrayList<EventSession>();
			EventSession session = null;
			Short previousCode = null;
			while (rs.next()) {
				Short code = rs.getShort("code");
				if (!code.equals(previousCode)) {
					session = new EventSession(code, rs.getString("title"), new DateTime(rs.getTimestamp("startTime")), new DateTime(rs.getTimestamp("endTime")), rs.getString("description"));
					sessions.add(session);
				}
				session.addLeader(new EventSessionLeader(rs.getString("firstName"), rs.getString("lastName")));				
				previousCode = code;
			}
			return sessions;			
		}
	};

}