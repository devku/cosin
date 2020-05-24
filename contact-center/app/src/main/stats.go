package main

import (
	"database/sql"
	"fmt"
	_ "github.com/go-sql-driver/mysql"
	"math/rand"
	"strconv"
	"time"
	"flag"
)

var dbClient *sql.DB
var startTime string
var endTime string

var dsn = flag.String("d", "", "Database source")

func getRandomString(length int) string {
	str := "0123456789abcdefghijklmnopqrstuvwxyz"
	bytes := []byte(str)
	result := []byte{}
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	for i := 0; i < length; i++ {
		result = append(result, bytes[r.Intn(len(bytes))])
	}
	return string(result)
}

func connectDB(dsn string) error {
	var err error

	if dbClient, err = sql.Open("mysql", dsn); err != nil {
		fmt.Printf("Open database error: %s\n", err)
		return err
	}

	if err = dbClient.Ping(); err != nil {
		fmt.Printf("Cannot connect to database: %s\n", err)
	}

	return err
}

func agentServiceFirstMsgTime() (error) {
	var err error
	rows, err := dbClient.Query("select id from uk_agentservice where logindate >= ? and logindate <= ?", startTime, endTime)
	if err != nil {
		return err
	}
	for rows.Next() {
		var agentServiceId string
		var firstMsgTime string
		rows.Scan(&agentServiceId)

		//first user msg
		err = dbClient.QueryRow("select createtime from uk_chat_message where agentserviceid = ? and calltype='呼入' and type='message' limit 0,1", agentServiceId).Scan(&firstMsgTime)
		if err == nil {
			dbClient.Exec("update uk_agentservice set userfirstmsgtime = ? where id=?",firstMsgTime, agentServiceId)
		}
		err = dbClient.QueryRow("select createtime from uk_chat_message where agentserviceid = ? and calltype='呼出' and type='message' limit 0,1", agentServiceId).Scan(&firstMsgTime)
		if err == nil {
			dbClient.Exec("update uk_agentservice set agentfirstmsgtime = ? where id=?",firstMsgTime, agentServiceId)
		}
	}
	return nil
}

func agentResponseDay() (error) {
	var err error

	//all agent
	agentRows, err := dbClient.Query("select id, username from cs_user")
	if err != nil {
		return err
	}
	for agentRows.Next() {
		var agentId string
		var agentUsername string
		var totalCount int
		var quitCount int
		var validCount int
		var maxRespTime int
		var minRespTime int
		var avgRespTime int
		var totalRespTime int

		agentRows.Scan(&agentId, &agentUsername)
		serviceRows, err := dbClient.Query("select id, userfirstmsgtime, agentfirstmsgtime from uk_agentservice where agentno=? and logindate >= ? and logindate <= ?",
			agentId, startTime, endTime)
		if err != nil {
			return err
		}

		minRespTime = 999999
		for serviceRows.Next() {
			var serviceId string
			var agentMsgTimeStr string
			var userMsgTimeStr string
			var agentMsgTimeUnix int64
			var userMsgTimeUnix int64
			var agentMsgTime time.Time
			var userMsgTime time.Time
			var respTime int64

			totalCount++

			serviceRows.Scan(&serviceId, &userMsgTimeStr, &agentMsgTimeStr)

			if userMsgTimeStr != "" && agentMsgTimeStr != "" {
				userMsgTime, err = time.Parse("2006-01-02 15:04:05", userMsgTimeStr)
				agentMsgTime, err = time.Parse("2006-01-02 15:04:05", agentMsgTimeStr)

				userMsgTimeUnix = userMsgTime.Unix()
				agentMsgTimeUnix = agentMsgTime.Unix()

				respTime = userMsgTimeUnix - agentMsgTimeUnix
				if respTime < 0 {
					respTime = 0
				}
				if minRespTime > (int)(respTime) {
					minRespTime = (int)(respTime)
				}
				if maxRespTime < (int)(respTime) {
					maxRespTime = (int)(respTime)
				}
				totalRespTime += (int)(respTime)
				validCount++
			}

			if userMsgTimeStr != "" && agentMsgTimeStr == "" {
				quitCount++
			}
		}

		if totalCount == 0 {
			continue
		}

		if validCount > 0 {
			avgRespTime = totalRespTime / validCount
		} else {
			maxRespTime = 0
			minRespTime = 0
		}

		statsId := ""
		err = dbClient.QueryRow("select id from uk_agent_response_stats where agentno=? and statsdate=?", agentId, startTime).Scan(&statsId)
		if err == sql.ErrNoRows {
			statsId = getRandomString(32)
			_, err = dbClient.Exec("insert into uk_agent_response_stats(id, agentno, agentusername, maxfirstresptime, minfirstresptime, avgfirstresptime, servicecount, quitservicecount, statsdate) values(?, ?, ?, ?, ?, ?, ?, ?, ?)",
				statsId, agentId, agentUsername, maxRespTime, minRespTime, avgRespTime, totalCount, quitCount, startTime)
		} else {
			_, err = dbClient.Exec("update uk_agent_response_stats set maxfirstresptime=?, minfirstresptime=?, avgfirstresptime=?, servicecount = ?, quitservicecount = ? where id=?",
				maxRespTime, minRespTime, avgRespTime, totalCount, quitCount, statsId)
		}
		if err != nil {
			fmt.Printf("%s\n", err)
		}
	}
	return nil
}


func agentServiceStatsDay() (error) {
	//agent service
	var agentServiceCountStr string
	err := dbClient.QueryRow("select count(1) from uk_agentservice where logindate >= ? and logindate <= ?", startTime, endTime).Scan(&agentServiceCountStr)
	if err != nil {
		return err
	}
	agentServiceCount, err := strconv.Atoi(agentServiceCountStr)
	if err != nil {
		return err
	}

	//leave msg
	msgCountStr := ""
	err = dbClient.QueryRow("select count(1) from uk_leavemsg where createtime >= ? and createtime <= ?", startTime, endTime).Scan(&msgCountStr)
	msgCount, err := strconv.Atoi(msgCountStr)

	//
	statsId := ""
	err = dbClient.QueryRow("select id from uk_agentservice_stats_day where statsdate=?", startTime).Scan(&statsId)
	if err == sql.ErrNoRows {
		statsId = getRandomString(32)
		_, err = dbClient.Exec("insert into uk_agentservice_stats_day(id, servicecount, guestcount, leavemsgcount, statsdate) values(?, ?, ?, ?, ?)",
			statsId, agentServiceCount, 0, msgCount, startTime)
	} else {
		_, err = dbClient.Exec("update uk_agentservice_stats_day set servicecount=?, guestcount=?, leavemsgcount=? where id=?",
			agentServiceCount, 0, msgCount, statsId)
	}
	if err != nil {
		fmt.Printf("%s\n", err)
	}

	return nil
}

func getTime() {
	//yesterdayTime := time.Now().AddDate(0, 0, -1)
	//startTime = yesterdayTime.Format("2006-01-02") + " 00:00:00"
	//endTime = yesterdayTime.Format("2006-01-02") + " 23:59:59"
	startTime = "2020-05-18 00:00:00"
	endTime = "2020-05-18 23:59:59"
}

func main() {
	flag.Parse()

	if *dsn == "" {
		fmt.Printf("Dsn cannot be empty\n")
		return
	}

	getTime()
	//connectDB("root:123456@(127.0.0.1)/cosinee?charset=utf8mb4")
	connectDB(*dsn)
	agentServiceFirstMsgTime()
	agentResponseDay()
	agentServiceStatsDay()
}
