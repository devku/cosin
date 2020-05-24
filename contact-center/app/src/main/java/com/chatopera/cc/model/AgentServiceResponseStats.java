/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "uk_agent_response_stats")
@org.hibernate.annotations.Proxy(lazy = false)
public class AgentServiceResponseStats implements java.io.Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1115593425069549681L;
	
	private String id ;
	private String agentno ;
	private String agentusername;
	private String skill;
	private int maxfirstresptime ;
	private int minfirstresptime;
	private int avgfirstresptime;
	private int servicecount;
	private int quitservicecount;
	private Date statsdate ;

	@Id
	@Column(length = 32)
	@GeneratedValue(generator = "system-uuid")
	@GenericGenerator(name = "system-uuid", strategy = "uuid")	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getAgentno() {
		return agentno;
	}
	public void setAgentno(String agentno) {
		this.agentno = agentno;
	}
	public String getAgentusername() {return agentusername;}
	public void setAgentusername(String agentusername) {this.agentusername = agentusername;}
	public String getSkill() {return skill;}
	public void setSkill(String sill) {this.skill = skill;}
	public int getMaxfirstresptime() { return maxfirstresptime;}
	public void setMaxfirstresptime(int maxfirstresptime) {this.maxfirstresptime = maxfirstresptime;}
	public int getMinfirstresptime() { return minfirstresptime;}
	public void setMinfirstresptime(int minfirstresptime) {this.minfirstresptime = minfirstresptime;}
	public int getAvgfirstresptime() { return avgfirstresptime;}
	public void setAvgfirstresptime(int avgfirstresptime) {this.avgfirstresptime = avgfirstresptime;}
	public int getServicecount() {return servicecount;}
	public void setServicecount(int servicecount){this.servicecount = servicecount;}
	public int getQuitservicecount() {return quitservicecount;}
	public void setQuitservicecount(int quitservicecount) {this.quitservicecount = quitservicecount;}
	public Date getStatsdate() {return statsdate;}
	public void setStatsdate(Date statsdate) {this.statsdate = statsdate;}
}
