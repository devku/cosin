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
 package com.chatopera.cc.controller.apps;

 import com.alibaba.fastjson.JSONObject;
 import com.chatopera.cc.acd.ACDAgentService;
 import com.chatopera.cc.acd.ACDPolicyService;
 import com.chatopera.cc.acd.ACDWorkMonitor;
 import com.chatopera.cc.activemq.BrokerPublisher;
 import com.chatopera.cc.basic.Constants;
 import com.chatopera.cc.basic.MainContext;
 import com.chatopera.cc.basic.MainUtils;
 import com.chatopera.cc.cache.Cache;
 import com.chatopera.cc.controller.Handler;
 import com.chatopera.cc.exception.CSKefuException;
 import com.chatopera.cc.model.*;
 import com.chatopera.cc.peer.PeerSyncIM;
 import com.chatopera.cc.persistence.blob.JpaBlobHelper;
 import com.chatopera.cc.persistence.es.ChatMessageEsRepository;
 import com.chatopera.cc.persistence.es.ContactsRepository;
 import com.chatopera.cc.persistence.es.QuickReplyRepository;
 import com.chatopera.cc.persistence.interfaces.DataExchangeInterface;
 import com.chatopera.cc.persistence.repository.*;
 import com.chatopera.cc.proxy.*;
 import com.chatopera.cc.socketio.message.ChatMessage;
 import com.chatopera.cc.socketio.message.Message;
 import com.chatopera.cc.util.Menu;
 import com.chatopera.cc.util.PinYinTools;
 import com.chatopera.cc.util.PropertiesEventUtil;
 import freemarker.template.TemplateException;
 import org.apache.commons.lang.StringUtils;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageRequest;
 import org.springframework.data.domain.Sort;
 import org.springframework.data.domain.Sort.Direction;
 import org.springframework.stereotype.Controller;
 import org.springframework.ui.ModelMap;
 import org.springframework.util.FileCopyUtils;
 import org.springframework.web.bind.annotation.RequestMapping;
 import org.springframework.web.bind.annotation.RequestParam;
 import org.springframework.web.bind.annotation.ResponseBody;
 import org.springframework.web.multipart.MultipartFile;
 import org.springframework.web.servlet.ModelAndView;

 import javax.servlet.http.HttpServletRequest;
 import javax.servlet.http.HttpServletResponse;
 import javax.validation.Valid;
 import java.io.File;
 import java.io.IOException;
 import java.text.ParseException;
 import java.text.SimpleDateFormat;
 import java.util.*;

 @Controller
 @RequestMapping("/agent")
 public class AgentController extends Handler {

     static final Logger logger = LoggerFactory.getLogger(AgentController.class);

     @Autowired
     private ACDWorkMonitor acdWorkMonitor;

     @Autowired
     private ACDPolicyService acdPolicyService;

     @Autowired
     private ACDAgentService acdAgentService;

     @Autowired
     private ContactsRepository contactsRes;

     @Autowired
     private PropertiesEventRepository propertiesEventRes;

     @Autowired
     private AgentUserRepository agentUserRes;

     @Autowired
     private AgentStatusRepository agentStatusRes;

     @Autowired
     private AgentServiceRepository agentServiceRes;

     @Autowired
     private OnlineUserRepository onlineUserRes;

     @Autowired
     private WeiXinUserRepository weiXinUserRes;

     @Autowired
     private ServiceSummaryRepository serviceSummaryRes;

     @Autowired
     private ChatMessageRepository chatMessageRes;

     @Autowired
     private ChatMessageEsRepository chatMessageEsRes;

     @Autowired
     private AgentProxy agentProxy;

     @Autowired
     private TagRepository tagRes;

     @Autowired
     private TagRelationRepository tagRelationRes;

     @Autowired
     private QuickReplyRepository quickReplyRes;

     @Autowired
     private QuickTypeRepository quickTypeRes;

     @Autowired
     private AgentUserTaskRepository agentUserTaskRes;

     @Autowired
     private UserRepository userRes;

     @Autowired
     private StatusEventRepository statusEventRes;

     @Autowired
     private AgentUserProxy agentUserProxy;

     @Autowired
     private PbxHostRepository pbxHostRes;

     @Autowired
     private AgentUserContactsRepository agentUserContactsRes;

     @Autowired
     private StreamingFileRepository streamingFileRes;

     @Autowired
     private JpaBlobHelper jpaBlobHelper;

     @Autowired
     private BlackEntityProxy blackEntityProxy;

     @Autowired
     private Cache cache;

     @Autowired
     private AgentServiceProxy agentServiceProxy;

     @Value("${web.upload-path}")
     private String webUploadPath;

     @Autowired
     private PeerSyncIM peerSyncIM;

     @Autowired
     private BrokerPublisher brokerPublisher;

     @Autowired
     private AgentStatusProxy agentStatusProxy;

     @Autowired
     private UserProxy userProxy;

     /**
      * 坐席从联系人列表进入坐席工作台和该联系人聊天
      *
      * @param map
      * @param request
      * @param response
      * @param sort
      * @param channels  可立即触达的渠道
      * @param contactid
      * @return
      * @throws IOException
      * @throws TemplateException
      */
     @RequestMapping("/proactive")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView proactive(
             ModelMap map,
             HttpServletRequest request,
             HttpServletResponse response,
             @Valid String sort,
             @Valid String channels,
             @RequestParam(name = "contactid", required = false) String contactid) throws IOException, TemplateException, CSKefuException {

         if (StringUtils.isBlank(contactid)) {
             logger.info("[chat] empty contactid, fast return error page.");
             return request(super.createRequestPageTempletResponse("/public/error"));
         }

         logger.info(
                 "[chat] contactid {}, channels {}", contactid,
                 channels);

         final User logined = super.getUser(request);
         final String orgi = logined.getOrgi();

         AgentUser agentUser = agentUserProxy.figureAgentUserBeforeChatWithContactInfo(channels, contactid, logined);

         if (agentUser != null) {
             logger.info(
                     "[chat] resolved agentUser, figure view model data as index page, agentUserId {}, onlineUser Id {}, agentno {}, channel {}",
                     agentUser.getId(), agentUser.getUserid(), agentUser.getAgentno(), agentUser.getChannel());
         } else {
             logger.info("[chat] can not resolve agentUser !!!");
         }

         // TODO 在agentUser没有得到的情况下，传回的前端信息增加提示，提示放在modelview中

         // 处理原聊天数据
         ModelAndView view = request(super.createAppsTempletResponse("/apps/agent/index"));
         agentUserProxy.buildIndexViewWithModels(view, map, request, response, sort, logined, orgi, agentUser);
         return view;
     }


     /**
      * 打开坐席工作台
      *
      * @param map
      * @param request
      * @param response
      * @param sort
      * @return
      * @throws IOException
      * @throws TemplateException
      */
     @RequestMapping("/index")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView index(
             ModelMap map,
             HttpServletRequest request,
             HttpServletResponse response,
             @Valid String sort) throws IOException, TemplateException {
         final User logined = super.getUser(request);
         final String orgi = logined.getOrgi();
         ModelAndView view = request(super.createAppsTempletResponse("/apps/agent/index"));
         agentUserProxy.buildIndexViewWithModels(view, map, request, response, sort, logined, orgi, null);
         return view;
     }

     @RequestMapping("/agentusers")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentusers(HttpServletRequest request, String userid) {
         ModelAndView view = request(super.createRequestPageTempletResponse("/apps/agent/agentusers"));
         User logined = super.getUser(request);
         view.addObject(
                 "agentUserList", agentUserRes.findByAgentnoAndOrgi(logined.getId(), logined.getOrgi(),
                         new Sort(Direction.DESC, "status")));
         List<AgentUser> agentUserList = agentUserRes.findByUseridAndOrgi(userid, logined.getOrgi());
         view.addObject(
                 "curagentuser", agentUserList != null && agentUserList.size() > 0 ? agentUserList.get(0) : null);

         return view;
     }

     @RequestMapping("/agentuserpage")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentuserpage(
             ModelMap map,
             HttpServletRequest request,
             String id,
             Integer page,
             Integer current) throws IOException, TemplateException {
         String mainagentuserconter = "/apps/agent/mainagentuserconter";
         ModelAndView view = request(super.createRequestPageTempletResponse(mainagentuserconter));
         AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, super.getOrgi(request));
         if (agentUser != null) {
             view.addObject("curagentuser", agentUser);
             view.addObject(
                     "agentUserMessageList", this.chatMessageRes.findByusession(agentUser.getUserid(), current));
         }
         return view;
     }

     @RequestMapping("/agentuserLabel")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentuserLabel(
             ModelMap map,
             HttpServletRequest request,
             String iconid) throws IOException, TemplateException {
         String mainagentuserconter = "/apps/agent/mainagentuserconter";
         ModelAndView view = request(super.createRequestPageTempletResponse(mainagentuserconter));
         ChatMessage labelid = this.chatMessageRes.findById(iconid);
         if (labelid != null) {
             if (labelid.isIslabel() == false) {
                 labelid.setIslabel(true);
             } else {
                 labelid.setIslabel(false);
             }
             chatMessageRes.save(labelid);
         }
         return view;
     }

     @RequestMapping("/agentusersearch")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentusersearch(
             ModelMap map,
             HttpServletRequest request,
             String id,
             String search,
             String condition
     ) throws IOException, TemplateException {
         String mainagentuserconter = "/apps/agent/mainagentusersearch";
         ModelAndView view = request(super.createRequestPageTempletResponse(mainagentuserconter));
         AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, super.getOrgi(request));

         if (agentUser != null) {
             Page<ChatMessage> agentUserMessageList = null;
             if (condition.equals("label")) {
                 agentUserMessageList = this.chatMessageRes.findByislabel(
                         agentUser.getUserid(), search, new PageRequest(0, 9999, Direction.DESC, "updatetime"));
             } else {
                 agentUserMessageList = this.chatMessageEsRes.findByUsessionAndMessageContaining(
                         agentUser.getUserid(), search, new PageRequest(0, 9999, Direction.DESC, "updatetime"));
             }
             view.addObject("agentUserMessageList", agentUserMessageList);
         }
         return view;
     }


     @RequestMapping("/agentusersearchdetails")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentusersearchdetails(
             ModelMap map,
             HttpServletRequest request,
             String id,
             String createtime,
             String thisid) throws IOException, TemplateException, ParseException {
         String mainagentuserconter = "/apps/agent/mainagentuserconter";
         ModelAndView view = request(super.createRequestPageTempletResponse(mainagentuserconter));
         AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, super.getOrgi(request));
         if (agentUser != null) {
             SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
             Date date = formatter.parse(createtime);
             view.addObject("agentusersearchdetails", thisid);
             view.addObject(
                     "agentUserMessageList", this.chatMessageRes.findByCreatetime(agentUser.getUserid(), date));
             view.addObject(
                     "agentUserMessageListnum", this.chatMessageRes.countByUsessionAndCreatetimeGreaterThanEqual(
                             agentUser.getUserid(), date));
         }
         return (view);
     }

     @RequestMapping("/agentuser")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView agentuser(
             ModelMap map,
             HttpServletRequest request,
             String id,
             String channel) throws IOException, TemplateException {
         // set default Value as WEBIM
         String mainagentuser = "/apps/agent/mainagentuser";
         switch (MainContext.ChannelType.toValue(channel)) {
             case PHONE:
                 mainagentuser = "/apps/agent/mainagentuser_callout";
                 break;
             case SKYPE:
                 mainagentuser = "/apps/agent/mainagentuser_skype";
                 break;
         }

         ModelAndView view = request(super.createRequestPageTempletResponse(mainagentuser));
         final User logined = super.getUser(request);
         final String orgi = logined.getOrgi();
         AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, orgi);

         if (agentUser != null) {
             view.addObject("curagentuser", agentUser);

             CousultInvite invite = OnlineUserProxy.consult(agentUser.getAppid(), agentUser.getOrgi());
             if (invite != null) {
                 view.addObject("aisuggest", invite.isAisuggest());
             }
             view.addObject("inviteData", OnlineUserProxy.consult(agentUser.getAppid(), agentUser.getOrgi()));
             List<AgentUserTask> agentUserTaskList = agentUserTaskRes.findByIdAndOrgi(id, orgi);
             if (agentUserTaskList.size() > 0) {
                 AgentUserTask agentUserTask = agentUserTaskList.get(0);
                 agentUserTask.setTokenum(0);
                 agentUserTaskRes.save(agentUserTask);
             }

             if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                 List<AgentServiceSummary> summarizes = this.serviceSummaryRes.findByAgentserviceidAndOrgi(
                         agentUser.getAgentserviceid(), orgi);
                 if (summarizes.size() > 0) {
                     view.addObject("summary", summarizes.get(0));
                 }
             }

             view.addObject(
                     "agentUserMessageList",
                     this.chatMessageRes.findByUsessionAndOrgi(agentUser.getUserid(), orgi,
                             new PageRequest(0, 20, Direction.DESC,
                                     "updatetime")));
             AgentService agentService = null;
             if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                 agentService = this.agentServiceRes.findOne(agentUser.getAgentserviceid());
                 view.addObject("curAgentService", agentService);
                 if (agentService != null) {
                     /**
                      * 获取关联数据
                      */
                     agentServiceProxy.processRelaData(logined.getId(), orgi, agentService, map);
                 }
             }
             if (MainContext.ChannelType.WEIXIN.toString().equals(agentUser.getChannel())) {
                 List<WeiXinUser> weiXinUserList = weiXinUserRes.findByOpenidAndOrgi(agentUser.getUserid(), orgi);
                 if (weiXinUserList.size() > 0) {
                     WeiXinUser weiXinUser = weiXinUserList.get(0);
                     view.addObject("weiXinUser", weiXinUser);
                 }
             } else if (MainContext.ChannelType.WEBIM.toString().equals(agentUser.getChannel())) {
                 OnlineUser onlineUser = onlineUserRes.findOne(agentUser.getUserid());
                 if (onlineUser != null) {
                     if (onlineUser.getLogintime() != null) {
                         if (MainContext.OnlineUserStatusEnum.OFFLINE.toString().equals(onlineUser.getStatus())) {
                             onlineUser.setBetweentime(
                                     (int) (onlineUser.getUpdatetime().getTime() - onlineUser.getLogintime().getTime()));
                         } else {
                             onlineUser.setBetweentime(
                                     (int) (System.currentTimeMillis() - onlineUser.getLogintime().getTime()));
                         }
                     }
                     view.addObject("onlineUser", onlineUser);
                 }
             } else if (MainContext.ChannelType.PHONE.toString().equals(agentUser.getChannel())) {
                 if (agentService != null && StringUtils.isNotBlank(agentService.getOwner())) {
                     StatusEvent statusEvent = this.statusEventRes.findById(agentService.getOwner());
                     if (statusEvent != null) {
                         if (StringUtils.isNotBlank(statusEvent.getHostid())) {
                             PbxHost pbxHost = pbxHostRes.findById(statusEvent.getHostid());
                             view.addObject("pbxHost", pbxHost);
                         }
                         view.addObject("statusEvent", statusEvent);
                     }
                 }
             }


             view.addObject("serviceCount", Integer
                     .valueOf(this.agentServiceRes
                             .countByUseridAndOrgiAndStatus(agentUser
                                             .getUserid(), orgi,
                                     MainContext.AgentUserStatusEnum.END
                                             .toString())));
             view.addObject("tagRelationList", tagRelationRes.findByUserid(agentUser.getUserid()));
         }

         SessionConfig sessionConfig = acdPolicyService.initSessionConfig(super.getOrgi(request));

         view.addObject("sessionConfig", sessionConfig);
         if (sessionConfig.isOtherquickplay()) {
             view.addObject("topicList", OnlineUserProxy.search(null, orgi, super.getUser(request)));
         }


         view.addObject("tags", tagRes.findByOrgiAndTagtype(orgi, MainContext.ModelType.USER.toString()));
         view.addObject(
                 "quickReplyList", quickReplyRes.findByOrgiAndCreater(orgi, super.getUser(request).getId(), null));
         List<QuickType> quickTypeList = quickTypeRes.findByOrgiAndQuicktype(
                 orgi, MainContext.QuickType.PUB.toString());
         List<QuickType> priQuickTypeList = quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                 orgi, MainContext.QuickType.PRI.toString(), super.getUser(request).getId());
         quickTypeList.addAll(priQuickTypeList);
         view.addObject("pubQuickTypeList", quickTypeList);

         return view;
     }

     @RequestMapping("/other/topic")
     @Menu(type = "apps", subtype = "othertopic")
     public ModelAndView othertopic(ModelMap map, HttpServletRequest request, String q) throws IOException, TemplateException {
         SessionConfig sessionConfig = acdPolicyService.initSessionConfig(super.getOrgi(request));

         map.put("sessionConfig", sessionConfig);
         if (sessionConfig.isOtherquickplay()) {
             map.put("topicList", OnlineUserProxy.search(q, super.getOrgi(request), super.getUser(request)));
         }

         return request(super.createRequestPageTempletResponse("/apps/agent/othertopic"));
     }

     @RequestMapping("/other/topic/detail")
     @Menu(type = "apps", subtype = "othertopicdetail")
     public ModelAndView othertopicdetail(ModelMap map, HttpServletRequest request, String id) throws IOException, TemplateException {
         SessionConfig sessionConfig = acdPolicyService.initSessionConfig(super.getOrgi(request));

         map.put("sessionConfig", sessionConfig);
         if (sessionConfig.isOtherquickplay()) {
             map.put("topic", OnlineUserProxy.detail(id, super.getOrgi(request), super.getUser(request)));
         }

         return request(super.createRequestPageTempletResponse("/apps/agent/topicdetail"));
     }


     @RequestMapping("/workorders/list")
     @Menu(type = "apps", subtype = "workorderslist")
     public ModelAndView workorderslist(HttpServletRequest request, String contactsid, ModelMap map) {
         if (MainContext.hasModule(Constants.CSKEFU_MODULE_WORKORDERS) && StringUtils.isNotBlank(contactsid)) {
             DataExchangeInterface dataExchange = (DataExchangeInterface) MainContext.getContext().getBean(
                     "workorders");
             if (dataExchange != null) {
                 map.addAttribute(
                         "workOrdersList",
                         dataExchange.getListDataByIdAndOrgi(contactsid, super.getUser(request).getId(),
                                 super.getOrgi(request)));
             }
             map.addAttribute("contactsid", contactsid);
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/workorders"));
     }

     /**
      * 设置为就绪，置闲
      *
      * @param request
      * @return
      */
     @RequestMapping(value = "/ready")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView ready(HttpServletRequest request) {
         final User logined = super.getUser(request);
         final String orgi = super.getOrgi(request);
         final AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentnoAndOrgi(
                 logined.getId(), orgi, logined.getSkills());

         // 缓存就绪状态
         agentProxy.ready(logined, agentStatus, false);

         // 为该坐席分配访客
         acdAgentService.assignVisitors(agentStatus.getAgentno(), orgi);
         acdWorkMonitor.recordAgentStatus(agentStatus.getAgentno(),
                 agentStatus.getUsername(),
                 agentStatus.getAgentno(),
                 logined.isAdmin(), // 0代表admin
                 agentStatus.getAgentno(),
                 MainContext.AgentStatusEnum.NOTREADY.toString(),
                 MainContext.AgentStatusEnum.READY.toString(),
                 MainContext.AgentWorkType.MEIDIACHAT.toString(),
                 orgi, null);

         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     /**
      * 将一个已经就绪的坐席设置为未就绪的状态
      * 这个接口并不会重新分配坐席的现在的服务中的访客给其它坐席
      *
      * @param request
      * @return
      */
     @RequestMapping(value = "/notready")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView notready(HttpServletRequest request) {
         final User logined = super.getUser(request);
         logger.info("[notready] set user {} as not ready", logined.getId());
         String orgi = super.getOrgi(request);

         AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentnoAndOrgi(
                 logined.getId(), logined.getOrgi(), logined.getSkills());

         agentStatus.setBusy(false);
         agentStatus.setUpdatetime(new Date());
         agentStatus.setStatus(MainContext.AgentStatusEnum.NOTREADY.toString());
         cache.putAgentStatusByOrgi(agentStatus, orgi);
         agentStatusRes.save(agentStatus);

         agentStatusProxy.broadcastAgentsStatus(orgi, "agent", "notready", agentStatus.getAgentno());

         acdWorkMonitor.recordAgentStatus(agentStatus.getAgentno(),
                 agentStatus.getUsername(),
                 agentStatus.getAgentno(),
                 logined.isAdmin(), // 0代表admin
                 agentStatus.getAgentno(),
                 MainContext.AgentStatusEnum.READY.toString(),
                 MainContext.AgentStatusEnum.NOTREADY.toString(),
                 MainContext.AgentWorkType.MEIDIACHAT.toString(),
                 orgi, null);

         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     /**
      * 设置状态：就绪，置忙
      *
      * @param request
      * @return
      */
     @RequestMapping(value = "/busy")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView busy(HttpServletRequest request) {
         final User logined = super.getUser(request);
         logger.info("[busy] set user {} as busy", logined.getId());
         AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentnoAndOrgi(
                 logined.getId(), logined.getOrgi(), logined.getSkills());

         agentStatus.setBusy(true);
         acdWorkMonitor.recordAgentStatus(
                 agentStatus.getAgentno(),
                 agentStatus.getUsername(),
                 agentStatus.getAgentno(),
                 super.getUser(request).isAdmin(),
                 agentStatus.getAgentno(),
                 MainContext.AgentStatusEnum.NOTBUSY.toString(),
                 MainContext.AgentStatusEnum.BUSY.toString(),
                 MainContext.AgentWorkType.MEIDIACHAT.toString(),
                 agentStatus.getOrgi(),
                 agentStatus.getUpdatetime());
         agentStatus.setUpdatetime(new Date());
         cache.putAgentStatusByOrgi(agentStatus, super.getOrgi(request));
         agentStatusRes.save(agentStatus);

         agentStatusProxy.broadcastAgentsStatus(super.getOrgi(request), "agent", "busy", logined.getId());

         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     /**
      * 设置状态：就绪，置闲
      *
      * @param request
      * @return
      */
     @RequestMapping(value = "/notbusy")
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView notbusy(HttpServletRequest request) {
         final User logined = super.getUser(request);
         // 组织结构和权限数据
         logger.info("[notbusy] set user {} as not busy", logined.getId());

         AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentnoAndOrgi(
                 logined.getId(), logined.getOrgi(), logined.getSkills());

         // 设置为就绪，置闲
         agentStatus.setBusy(false);
         agentStatus.setUpdatetime(new Date());
         agentStatus.setStatus(MainContext.AgentStatusEnum.READY.toString());

         // 更新工作记录
         acdWorkMonitor.recordAgentStatus(
                 agentStatus.getAgentno(),
                 agentStatus.getUsername(),
                 agentStatus.getAgentno(),
                 super.getUser(request).isAdmin(),
                 agentStatus.getAgentno(),
                 MainContext.AgentStatusEnum.BUSY.toString(),
                 MainContext.AgentStatusEnum.NOTBUSY.toString(),
                 MainContext.AgentWorkType.MEIDIACHAT.toString(),
                 agentStatus.getOrgi(),
                 agentStatus.getUpdatetime());

         // 更新数据库和缓存
         cache.putAgentStatusByOrgi(agentStatus, super.getOrgi(request));
         agentStatusRes.save(agentStatus);

         // 重新分配访客给坐席
         acdAgentService.assignVisitors(agentStatus.getAgentno(), super.getOrgi(request));

         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     @RequestMapping(value = "/clean")
     @Menu(type = "apps", subtype = "clean", access = false)
     public ModelAndView clean(HttpServletRequest request) throws Exception {
         final String orgi = super.getOrgi(request);
         List<AgentUser> agentUserList = agentUserRes.findByAgentnoAndStatusAndOrgi(
                 super.getUser(request).getId(), MainContext.AgentUserStatusEnum.END.toString(),
                 super.getOrgi(request));
         List<AgentService> agentServiceList = new ArrayList<AgentService>();
         for (AgentUser agentUser : agentUserList) {
             if (agentUser != null && super.getUser(request).getId().equals(agentUser.getAgentno())) {
                 acdAgentService.finishAgentUser(agentUser, orgi);
                 AgentService agentService = agentServiceRes.findByIdAndOrgi(agentUser.getAgentserviceid(), orgi);
                 if (agentService != null) {
                     agentService.setStatus(MainContext.AgentUserStatusEnum.END.toString());
                     agentServiceList.add(agentService);
                 }
             }
         }
         agentServiceRes.save(agentServiceList);
         return request(super
                 .createRequestPageTempletResponse("redirect:/agent/index.html"));
     }


     /**
      * 结束对话
      * 如果当前对话属于登录用户或登录用户为超级用户，则可以结束这个对话
      *
      * @param request
      * @param id
      * @return
      * @throws Exception
      */
     @RequestMapping({"/end"})
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView end(HttpServletRequest request, @Valid String id) {
         logger.info("[end] end id {}", id);
         final String orgi = super.getOrgi(request);
         final User logined = super.getUser(request);

         final AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, orgi);

         if (agentUser != null) {
             if ((StringUtils.equals(
                     logined.getId(), agentUser.getAgentno()) || logined.isAdmin())) {
                 // 删除访客-坐席关联关系，包括缓存
                 try {
                     acdAgentService.finishAgentUser(agentUser, orgi);
                 } catch (CSKefuException e) {
                     // 未能删除成功
                     logger.error("[end]", e);
                 }
             } else {
                 logger.info("[end] Permission not fulfill.");
             }
         }

         return request(super
                 .createRequestPageTempletResponse("redirect:/agent/index.html"));
     }

     @RequestMapping({"/readmsg"})
     @Menu(type = "apps", subtype = "agent")
     public ModelAndView readmsg(HttpServletRequest request, @Valid String userid) {
         List<AgentUserTask> agentUserTaskList = agentUserTaskRes.findByIdAndOrgi(userid, super.getOrgi(request));
         if (agentUserTaskList.size() > 0) {
             AgentUserTask agentUserTask = agentUserTaskList.get(0);
             agentUserTask.setTokenum(0);
             agentUserTaskRes.save(agentUserTask);
         }
         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     @RequestMapping({"/blacklist/add"})
     @Menu(type = "apps", subtype = "blacklist")
     public ModelAndView blacklistadd(ModelMap map, HttpServletRequest request, @Valid String agentuserid, @Valid String agentserviceid, @Valid String userid)
             throws Exception {
         map.addAttribute("agentuserid", agentuserid);
         map.addAttribute("agentserviceid", agentserviceid);
         map.addAttribute("userid", userid);
         map.addAttribute("agentUser", agentUserRes.findByIdAndOrgi(userid, super.getOrgi(request)));
         return request(super.createRequestPageTempletResponse("/apps/agent/blacklistadd"));
     }

     @RequestMapping({"/blacklist/save"})
     @Menu(type = "apps", subtype = "blacklist")
     public ModelAndView blacklist(
             HttpServletRequest request,
             @Valid String agentuserid,
             @Valid String agentserviceid,
             @Valid String userid,
             @Valid BlackEntity blackEntity)
             throws Exception {
         logger.info("[blacklist] userid {}", userid);
         final User logined = super.getUser(request);
         final String orgi = logined.getOrgi();

         if (StringUtils.isBlank(userid)) {
             throw new CSKefuException("Invalid userid");
         }
         /**
          * 添加黑名单
          * 一定时间后触发函数
          */
         JSONObject payload = new JSONObject();

         int timeSeconds = blackEntity.getControltime() * 3600;
         payload.put("userId", userid);
         payload.put("orgi", orgi);
         ModelAndView view = end(request, agentuserid);

         // 更新或创建黑名单
         blackEntityProxy.updateOrCreateBlackEntity(blackEntity, logined, userid, orgi, agentserviceid, agentuserid);

         // 创建定时任务 取消拉黑
         brokerPublisher.send(
                 Constants.WEBIM_SOCKETIO_ONLINE_USER_BLACKLIST, payload.toJSONString(), false, timeSeconds);

         return view;
     }

     @RequestMapping("/tagrelation")
     @Menu(type = "apps", subtype = "tagrelation")
     public ModelAndView tagrelation(ModelMap map, HttpServletRequest request, @Valid String userid, @Valid String tagid, @Valid String dataid) {
         TagRelation tagRelation = tagRelationRes.findByUseridAndTagid(userid, tagid);
         if (tagRelation == null) {
             tagRelation = new TagRelation();
             tagRelation.setUserid(userid);
             tagRelation.setTagid(tagid);
             tagRelation.setDataid(dataid);
             tagRelationRes.save(tagRelation);
         } else {
             tagRelationRes.delete(tagRelation);
         }
         return request(super.createRequestPageTempletResponse("/public/success"));
     }

     /**
      * 坐席聊天时发送图片和文件
      *
      * @param map
      * @param request
      * @param multipart
      * @param id
      * @param paste     是否是粘贴到chatbox的图片事件，此时发送者还没有执行发送
      * @return
      * @throws IOException
      */
     @RequestMapping("/image/upload")
     @Menu(type = "im", subtype = "image", access = false)
     public ModelAndView upload(
             ModelMap map,
             HttpServletRequest request,
             @RequestParam(value = "imgFile", required = false) MultipartFile multipart,
             @Valid String id,
             @Valid boolean paste) throws IOException {
         logger.info("[upload] image file, agentUser id {}, paste {}", id, paste);
         final User logined = super.getUser(request);
         final String orgi = super.getOrgi(request);
         ModelAndView view = request(super.createRequestPageTempletResponse("/apps/agent/upload"));
         UploadStatus notify;
         final AgentUser agentUser = agentUserRes.findByIdAndOrgi(id, orgi);

         if (multipart != null && multipart.getOriginalFilename().lastIndexOf(".") > 0) {
             try {
                 StreamingFile sf = agentProxy.saveFileIntoMySQLBlob(logined, multipart);
                 // 发送通知
                 if (!paste) {
                     agentProxy.sendFileMessageByAgent(logined, agentUser, multipart, sf);
                 }
                 notify = new UploadStatus("0", sf.getFileUrl());
             } catch (CSKefuException e) {
                 notify = new UploadStatus("请选择文件");
             }
         } else {
             notify = new UploadStatus("请选择图片文件");
         }
         map.addAttribute("upload", notify);
         return view;
     }

     @RequestMapping("/message/image")
     @Menu(type = "resouce", subtype = "image", access = true)
     public ModelAndView messageimage(HttpServletResponse response, ModelMap map, @Valid String id, @Valid String t) throws IOException {
         ChatMessage message = chatMessageRes.findById(id);
         map.addAttribute("chatMessage", message);
         map.addAttribute("agentUser", cache.findOneAgentUserByUserIdAndOrgi(message.getUserid(), message.getOrgi()));
    	/*if(StringUtils.isNotBlank(t)){
    		map.addAttribute("t", t) ;
    	}*/
         map.addAttribute("t", true);
         return request(super.createRequestPageTempletResponse("/apps/agent/media/messageimage"));
     }

     @RequestMapping("/message/image/upload")
     @Menu(type = "im", subtype = "image", access = false)
     public ModelAndView messageimage(
             ModelMap map,
             HttpServletRequest request,
             @RequestParam(value = "image", required = false) MultipartFile image,
             @Valid String id,
             @Valid String userid,
             @Valid String fileid) throws IOException {
         logger.info("[messageimage] userid {}, chat message id {}, fileid {}", userid, id, fileid);
         if (image != null && StringUtils.isNotBlank(fileid)) {
             File tempFile = File.createTempFile(fileid, ".png");
             try {
                 // 创建临时图片文件
                 if (!tempFile.getParentFile().exists()) {
                     tempFile.getParentFile().mkdirs();
                 }
                 // 写入临时文件
                 FileCopyUtils.copy(image.getBytes(), tempFile);
                 ChatMessage chatMessage = chatMessageRes.findById(id);
                 chatMessage.setCooperation(true);
                 chatMessageRes.save(chatMessage);

                 // 写入协作文件
                 String fileName = "upload/" + fileid + "_cooperation";
                 File imageFile = new File(webUploadPath, fileName);
                 MainUtils.scaleImage(imageFile, tempFile, 0.1F);

                 // 保存到数据库
                 StreamingFile sf = streamingFileRes.findOne(fileid);
                 if (sf != null) {
                     sf.setCooperation(jpaBlobHelper.createBlobWithFile(imageFile));
                     streamingFileRes.save(sf);
                 }

                 cache.findOneAgentUserByUserIdAndOrgi(
                         chatMessage.getUserid(), chatMessage.getOrgi()).ifPresent(p -> {
                     Message outMessage = new Message();
                     outMessage.setMessage("/res/image.html?id=" + fileid + "&cooperation=true");
                     outMessage.setFilename(imageFile.getName());
                     outMessage.setAttachmentid(chatMessage.getAttachmentid());
                     outMessage.setFilesize((int) imageFile.length());
                     outMessage.setMessageType(MainContext.MediaType.ACTION.toString());
                     outMessage.setCalltype(MainContext.CallType.INVITE.toString());
                     outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(new Date()));
                     outMessage.setAgentUser(p);

                     peerSyncIM.send(
                             MainContext.ReceiverType.VISITOR,
                             MainContext.ChannelType.toValue(p.getChannel()),
                             p.getAppid(),
                             MainContext.MessageType.MESSAGE,
                             p.getUserid(),
                             outMessage,
                             true);
                 });
             } finally {
                 if (tempFile.exists()) {
                     tempFile.delete();
                 }
             }
         }
         return request(super.createRequestPageTempletResponse("/public/success"));
     }


     /**
      * 坐席会话关联联系人
      *
      * @param map
      * @param request
      * @param contactsid     联系人ID
      * @param userid         访客ID
      * @param agentserviceid 坐席服务ID
      * @param agentuserid    坐席ID
      * @return
      */
     @RequestMapping(value = "/contacts")
     @Menu(type = "apps", subtype = "contacts")
     public ModelAndView contacts(
             ModelMap map,
             final HttpServletRequest request,
             @Valid String contactsid,
             @Valid String userid,
             @Valid String agentserviceid,
             @Valid String agentuserid) throws CSKefuException {
         logger.info(
                 "[contacts] contactsid {}, userid {}, agentserviceid {}, agentuserid {}", contactsid, userid,
                 agentserviceid, agentuserid);

         final User logined = super.getUser(request);
         final String orgi = logined.getOrgi();

         if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(contactsid)) {

             /**
              * 获得联系人
              */
             Contacts contacts = contactsRes.findOne(contactsid);
             if (contacts != null) {
                 map.addAttribute("contacts", contacts);
             }

             /**
              * 在关联联系人后，更新AgentUser的显示的名字
              */
             AgentUser agentUser = agentUserRes.findByIdAndOrgi(agentuserid, orgi);
             if (agentUser != null) {
                 agentUser.setUsername(contacts.getName());
                 agentUser.setNickname(contacts.getName());
                 agentUserRes.save(agentUser);
             }

             /**
              * 更新OnlineUser
              */
             OnlineUser onlineUser = onlineUserRes.findOneByUseridAndOrgi(userid, agentUser.getOrgi());
             onlineUser.setContactsid(contactsid);
             onlineUser.setUsername(contacts.getName());
             onlineUser.setUpdateuser(logined.getUname());
             onlineUserRes.save(onlineUser);

             AgentService agentService = agentServiceRes.findOne(agentserviceid);
             if (agentService != null) {
                 agentService.setContactsid(contactsid);
                 agentService.setUsername(contacts.getName());
                 agentServiceRes.save(agentService);

                 AgentUserContacts agentUserContacts = agentUserContactsRes.findOneByUseridAndOrgi(
                         userid, orgi).orElseGet(() -> {
                     AgentUserContacts p = new AgentUserContacts();

                     p.setUserid(userid);
                     p.setCreater(super.getUser(request).getId());
                     p.setOrgi(super.getOrgi(request));
                     p.setCreatetime(new Date());
                     return p;
                 });

                 agentUserContacts.setContactsid(contactsid);
                 agentUserContacts.setAppid(agentService.getAppid());
                 agentUserContacts.setChannel(agentService.getChannel());

                 agentUserContactsRes.save(agentUserContacts);
             }
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/contacts"));
     }


     @RequestMapping(value = "/clean/associated")
     @Menu(type = "apps", subtype = "cleanassociated")
     public ModelAndView cleanAssociated(ModelMap map, HttpServletRequest request, final @RequestParam String currentAgentUserContactsId) {
         String contactsid = null;
         final String orgi = super.getOrgi(request);
         if (StringUtils.isNotEmpty(currentAgentUserContactsId)) {
             AgentUserContacts agentUserContacts = agentUserContactsRes.getOne(currentAgentUserContactsId);
             if (agentUserContacts != null) {
                 agentUserContactsRes.delete(agentUserContacts);
             }
         }

         return request(super.createRequestPageTempletResponse("/apps/agent/contacts"));
     }

     @ResponseBody
     @RequestMapping(value = "/evaluation")
     @Menu(type = "apps", subtype = "evaluation")
     public String evaluation(HttpServletRequest request, @Valid String agentuserid) {
         AgentUser agentUser = agentUserRes.findByIdAndOrgi(agentuserid, super.getOrgi(request));

         Message outMessage = new Message();
         outMessage.setChannelMessage(agentUser);
         outMessage.setAgentUser(agentUser);

         peerSyncIM.send(
                 MainContext.ReceiverType.VISITOR,
                 MainContext.ChannelType.toValue(agentUser.getChannel()),
                 agentUser.getAppid(),
                 MainContext.MessageType.SATISFACTION,
                 agentUser.getUserid(),
                 outMessage,
                 true);

         return "ok";
     }

     @RequestMapping(value = "/summary")
     @Menu(type = "apps", subtype = "summary")
     public ModelAndView summary(
             ModelMap map,
             HttpServletRequest request,
             @Valid String userid,
             @Valid String agentserviceid,
             @Valid String agentuserid,
             @Valid String channel) {
         if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
             AgentUser agentUser = this.agentUserRes.findByIdAndOrgi(agentuserid, super.getOrgi(request));
             if (agentUser != null && StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                 List<AgentServiceSummary> summaries = this.serviceSummaryRes.findByAgentserviceidAndOrgi(
                         agentUser.getAgentserviceid(), super.getOrgi(request));
                 if (summaries.size() > 0) {
                     map.addAttribute("summary", summaries.get(0));
                 }
             }

             map.addAttribute(
                     "tags", tagRes.findByOrgiAndTagtype(
                             super.getOrgi(request),
                             MainContext.ModelType.SUMMARY.toString()));
             map.addAttribute("userid", userid);
             map.addAttribute("agentserviceid", agentserviceid);
             map.addAttribute("agentuserid", agentuserid);
             map.addAttribute("channel", channel);
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/summary"));
     }

     @RequestMapping(value = "/summary/save")
     @Menu(type = "apps", subtype = "summarysave")
     public ModelAndView summarysave(
             ModelMap map,
             HttpServletRequest request,
             @Valid AgentServiceSummary summary,
             @Valid String contactsid,
             @Valid String userid,
             @Valid String agentserviceid,
             @Valid String agentuserid,
             @Valid String channel) {
         if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
             final String orgi = super.getOrgi(request);
             summary.setOrgi(orgi);
             summary.setCreater(super.getUser(request).getId());
             summary.setCreatetime(new Date());
             AgentService service = agentServiceRes.findByIdAndOrgi(agentserviceid, orgi);
             summary.setAgent(service.getAgentno());
             summary.setAgentno(service.getAgentno());
             summary.setUsername(service.getUsername());
             summary.setAgentusername(service.getAgentusername());
             summary.setChannel(service.getChannel());
             summary.setContactsid(contactsid);
             summary.setLogindate(service.getLogindate());
             summary.setContactsid(service.getContactsid());
             summary.setEmail(service.getEmail());
             summary.setPhonenumber(service.getPhone());
             serviceSummaryRes.save(summary);
             Tag tag = tagRes.findByOrgiAndId(orgi, summary.getServicetype());
             service.setServicetype(tag.getTag());
             agentServiceRes.save(service);
         }

         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/agentuser.html?id=" + agentuserid + "&channel=" + channel));
     }

     /**
      * 坐席转接窗口
      *
      * @param map
      * @param request
      * @param userid
      * @param agentserviceid
      * @param agentuserid
      * @return
      */
     @RequestMapping(value = "/transfer")
     @Menu(type = "apps", subtype = "transfer")
     public ModelAndView transfer(
             ModelMap map,
             final HttpServletRequest request,
             final @Valid String userid,
             final @Valid String agentserviceid,
             final @Valid String agentuserid) {
         logger.info("[transfer] userId {}, agentUser {}", userid, agentuserid);
         final String orgi = super.getOrgi(request);
         final User logined = super.getUser(request);
         if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
             // 列出所有技能组
             final List<Organ> skillGroups = OnlineUserProxy.organ(orgi, true);

             // DEBUG
             StringBuffer sb = new StringBuffer();
             for (final Organ organ : skillGroups) {
                 sb.append(organ.getId());
                 sb.append(":");
                 sb.append(organ.getName());
                 sb.append("\t");
             }
             logger.info("[transfer] skillGroups {}", sb.toString());


             // 选择当前用户的默认技能组
             Set<String> organs = logined.getOrgans().keySet();
             String currentOrgan = organs.size() > 0 ? (new ArrayList<String>(organs)).get(0) : null;

             if (StringUtils.isBlank(currentOrgan)) {
                 if (!skillGroups.isEmpty()) {
                     currentOrgan = skillGroups.get(0).getId();
                 }
             }
             logger.info("[transfer] set current organ as {}", currentOrgan);


             // 列出所有在线的坐席，排除本身
             final Map<String, AgentStatus> agentStatusMap = cache.findAllReadyAgentStatusByOrgi(orgi);

             List<String> userids = new ArrayList<>();

             for (final String o : agentStatusMap.keySet()) {
//                 logger.info("[transfer] agent status ready {}, status {}", agentStatusMap.get(o).getAgentno(), agentStatusMap.get(o).getStatus());
                 if (!StringUtils.equals(o, logined.getId())) {
                     userids.add(o);
                 }
             }

             logger.info("[transfer] get all userids except mine, {}", StringUtils.join(userids, "\t"));

             final List<User> userList = userRes.findAll(userids);
             for (final User o : userList) {
                 o.setAgentStatus(agentStatusMap.get(o.getId()));
                 // find user's skills
                 userProxy.attachOrgansPropertiesForUser(o);
             }

             map.addAttribute("userList", userList);
             map.addAttribute("userid", userid);
             map.addAttribute("agentserviceid", agentserviceid);
             map.addAttribute("agentuserid", agentuserid);
             map.addAttribute("skillGroups", skillGroups);
             map.addAttribute("agentservice", this.agentServiceRes.findByIdAndOrgi(agentserviceid, orgi));
             map.addAttribute("currentorgan", currentOrgan);
         }

         return request(super.createRequestPageTempletResponse("/apps/agent/transfer"));
     }

     /**
      * 查找一个组织机构中的在线坐席
      *
      * @param map
      * @param request
      * @param organ
      * @return
      */
     @RequestMapping(value = "/transfer/agent")
     @Menu(type = "apps", subtype = "transferagent")
     public ModelAndView transferagent(
             ModelMap map,
             HttpServletRequest request,
             @Valid String organ) {
         final User logined = super.getUser(request);
         final String orgi = super.getOrgi(request);
         if (StringUtils.isNotBlank(organ)) {
             List<String> usersids = new ArrayList<String>();
             final List<AgentStatus> agentStatusList = cache.getAgentStatusBySkillAndOrgi(organ, orgi);
             if (agentStatusList.size() > 0) {
                 for (AgentStatus agentStatus : agentStatusList) {
                     if (agentStatus != null && !StringUtils.equals(agentStatus.getAgentno(), logined.getId())) {
                         usersids.add(agentStatus.getAgentno());
                     }
                 }
             }
             List<User> userList = userRes.findAll(usersids);
             for (User user : userList) {
                 userProxy.attachOrgansPropertiesForUser(user);
                 for (final AgentStatus as : agentStatusList) {
                     if (StringUtils.equals(as.getAgentno(), user.getId())) {
                         user.setAgentStatus(as);
                         break;
                     }
                 }
             }
             map.addAttribute("userList", userList);
             map.addAttribute("currentorgan", organ);
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/transferagentlist"));
     }


     @RequestMapping("/quicklist")
     @Menu(type = "setting", subtype = "quickreply", admin = true)
     public ModelAndView quicklist(ModelMap map, HttpServletRequest request, @Valid String typeid) {
         map.addAttribute(
                 "quickReplyList",
                 quickReplyRes.findByOrgiAndCreater(super.getOrgi(request), super.getUser(request).getId(), null));
         List<QuickType> quickTypeList = quickTypeRes.findByOrgiAndQuicktype(
                 super.getOrgi(request), MainContext.QuickType.PUB.toString());
         List<QuickType> priQuickTypeList = quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                 super.getOrgi(request), MainContext.QuickType.PRI.toString(), super.getUser(request).getId());
         quickTypeList.addAll(priQuickTypeList);
         map.addAttribute("pubQuickTypeList", quickTypeList);

         if (StringUtils.isNotBlank(typeid)) {
             map.addAttribute("quickType", quickTypeRes.findByIdAndOrgi(typeid, super.getOrgi(request)));
         }

         return request(super.createRequestPageTempletResponse("/apps/agent/quicklist"));
     }

     @RequestMapping("/quickreply/add")
     @Menu(type = "setting", subtype = "quickreplyadd", admin = true)
     public ModelAndView quickreplyadd(ModelMap map, HttpServletRequest request, @Valid String parentid) {
         if (StringUtils.isNotBlank(parentid)) {
             map.addAttribute("quickType", quickTypeRes.findByIdAndOrgi(parentid, super.getOrgi(request)));
         }
         map.addAttribute(
                 "quickTypeList", quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                         super.getOrgi(request),
                         MainContext.QuickType.PRI.toString(),
                         super.getUser(request).getId()));
         return request(super.createRequestPageTempletResponse("/apps/agent/quickreply/add"));
     }

     @RequestMapping("/quickreply/save")
     @Menu(type = "setting", subtype = "quickreply", admin = true)
     public ModelAndView quickreplysave(ModelMap map, HttpServletRequest request, @Valid QuickReply quickReply) {
         if (StringUtils.isNotBlank(quickReply.getTitle()) && StringUtils.isNotBlank(quickReply.getContent())) {
             quickReply.setOrgi(super.getOrgi(request));
             quickReply.setCreater(super.getUser(request).getId());
             quickReply.setType(MainContext.QuickType.PRI.toString());
             quickReplyRes.save(quickReply);
         }
         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/quicklist.html?typeid=" + quickReply.getCate()));
     }

     @RequestMapping("/quickreply/delete")
     @Menu(type = "setting", subtype = "quickreply", admin = true)
     public ModelAndView quickreplydelete(ModelMap map, HttpServletRequest request, @Valid String id) {
         QuickReply quickReply = quickReplyRes.findOne(id);
         if (quickReply != null) {
             quickReplyRes.delete(quickReply);
         }
         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/quicklist.html?typeid=" + quickReply.getCate()));
     }

     @RequestMapping("/quickreply/edit")
     @Menu(type = "setting", subtype = "quickreply", admin = true)
     public ModelAndView quickreplyedit(ModelMap map, HttpServletRequest request, @Valid String id) {
         QuickReply quickReply = quickReplyRes.findOne(id);
         map.put("quickReply", quickReply);
         if (quickReply != null) {
             map.put("quickType", quickTypeRes.findByIdAndOrgi(quickReply.getCate(), super.getOrgi(request)));
         }
         map.addAttribute(
                 "quickTypeList", quickTypeRes.findByOrgiAndQuicktype(
                         super.getOrgi(request),
                         MainContext.QuickType.PUB.toString()));
         return request(super.createRequestPageTempletResponse("/apps/agent/quickreply/edit"));
     }

     @RequestMapping("/quickreply/update")
     @Menu(type = "setting", subtype = "quickreply", admin = true)
     public ModelAndView quickreplyupdate(ModelMap map, HttpServletRequest request, @Valid QuickReply quickReply) {
         if (StringUtils.isNotBlank(quickReply.getId())) {
             QuickReply temp = quickReplyRes.findOne(quickReply.getId());
             quickReply.setOrgi(super.getOrgi(request));
             quickReply.setCreater(super.getUser(request).getId());
             if (temp != null) {
                 quickReply.setCreatetime(temp.getCreatetime());
             }
             quickReply.setType(MainContext.QuickType.PUB.toString());
             quickReplyRes.save(quickReply);
         }
         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/quicklist.html?typeid=" + quickReply.getCate()));
     }

     @RequestMapping({"/quickreply/addtype"})
     @Menu(type = "apps", subtype = "kbs")
     public ModelAndView addtype(ModelMap map, HttpServletRequest request, @Valid String typeid) {
         map.addAttribute(
                 "quickTypeList", quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                         super.getOrgi(request),
                         MainContext.QuickType.PRI.toString(),
                         super.getUser(request).getId()));
         if (StringUtils.isNotBlank(typeid)) {
             map.addAttribute("quickType", quickTypeRes.findByIdAndOrgi(typeid, super.getOrgi(request)));
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/quickreply/addtype"));
     }

     @RequestMapping("/quickreply/type/save")
     @Menu(type = "apps", subtype = "kbs")
     public ModelAndView typesave(HttpServletRequest request, @Valid QuickType quickType) {
         int count = quickTypeRes.countByOrgiAndNameAndParentid(
                 super.getOrgi(request), quickType.getName(), quickType.getParentid());
         if (count == 0) {
             quickType.setOrgi(super.getOrgi(request));
             quickType.setCreater(super.getUser(request).getId());
             quickType.setCreatetime(new Date());
             quickType.setQuicktype(MainContext.QuickType.PRI.toString());
             quickTypeRes.save(quickType);
         }
         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/quicklist.html?typeid=" + quickType.getParentid()));
     }

     @RequestMapping({"/quickreply/edittype"})
     @Menu(type = "apps", subtype = "kbs")
     public ModelAndView edittype(ModelMap map, HttpServletRequest request, String id) {
         map.addAttribute("quickType", quickTypeRes.findByIdAndOrgi(id, super.getOrgi(request)));
         map.addAttribute(
                 "quickTypeList", quickTypeRes.findByOrgiAndQuicktypeAndCreater(
                         super.getOrgi(request),
                         MainContext.QuickType.PRI.toString(),
                         super.getUser(request).getId()));
         return request(super.createRequestPageTempletResponse("/apps/agent/quickreply/edittype"));
     }

     @RequestMapping("/quickreply/type/update")
     @Menu(type = "apps", subtype = "kbs")
     public ModelAndView typeupdate(HttpServletRequest request, @Valid QuickType quickType) {
         QuickType tempQuickType = quickTypeRes.findByIdAndOrgi(quickType.getId(), super.getOrgi(request));
         if (tempQuickType != null) {
             tempQuickType.setName(quickType.getName());
             tempQuickType.setDescription(quickType.getDescription());
             tempQuickType.setInx(quickType.getInx());
             tempQuickType.setParentid(quickType.getParentid());
             quickTypeRes.save(tempQuickType);
         }
         return request(
                 super.createRequestPageTempletResponse("redirect:/agent/quicklist.html?typeid=" + quickType.getId()));
     }

     @RequestMapping({"/quickreply/deletetype"})
     @Menu(type = "apps", subtype = "kbs")
     public ModelAndView deletetype(ModelMap map, HttpServletRequest request, @Valid String id) {
         QuickType tempQuickType = quickTypeRes.findByIdAndOrgi(id, super.getOrgi(request));
         if (tempQuickType != null) {
             quickTypeRes.delete(tempQuickType);

             Page<QuickReply> quickReplyList = quickReplyRes.getByOrgiAndCate(
                     super.getOrgi(request), id, null, new PageRequest(0, 10000));

             quickReplyRes.delete(quickReplyList.getContent());
         }
         return request(super.createRequestPageTempletResponse(
                 "redirect:/agent/quicklist.html" + (tempQuickType != null ? "?typeid=" + tempQuickType.getParentid() : "")));
     }

     @RequestMapping({"/quickreply/content"})
     @Menu(type = "apps", subtype = "quickreply")
     public ModelAndView quickreplycontent(ModelMap map, HttpServletRequest request, @Valid String id) {
         QuickReply quickReply = quickReplyRes.findOne(id);
         if (quickReply != null) {
             map.addAttribute("quickReply", quickReply);
         }
         return request(super.createRequestPageTempletResponse("/apps/agent/quickreplycontent"));
     }

     @RequestMapping("/calloutcontact/add")
     @Menu(type = "apps", subtype = "calloutcontact", admin = true)
     public ModelAndView add(ModelMap map, HttpServletRequest request, @Valid String ckind) {
         map.addAttribute("ckind", ckind);
         return request(super.createRequestPageTempletResponse("/apps/agent/calloutcontact/add"));
     }

     @RequestMapping(value = "/calloutcontact/save")
     @Menu(type = "apps", subtype = "calloutcontact")
     public ModelAndView calloutcontactsave(
             ModelMap map,
             HttpServletRequest request,
             @RequestParam(value = "agentuser", required = true) String agentuser,
             @Valid Contacts contacts) throws CSKefuException {
         logger.info("[agent ctrl] calloutcontactsave agentuser [{}]", agentuser);
         AgentUser au = agentUserRes.findOne(agentuser);
         if (au == null) {
             throw new CSKefuException("不存在该服务记录");
         }

         User logined = super.getUser(request);
         contacts.setId(MainUtils.getUUID());
         contacts.setCreater(logined.getId());
         contacts.setOrgi(logined.getOrgi());
         contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
         if (StringUtils.isBlank(contacts.getCusbirthday())) {
             contacts.setCusbirthday(null);
         }
         contactsRes.save(contacts);

         AgentUserContacts auc = new AgentUserContacts();
         auc.setId(MainUtils.getUUID());
         auc.setUsername(au.getUsername());
         auc.setOrgi(MainContext.SYSTEM_ORGI);
         auc.setUserid(au.getUserid());
         auc.setContactsid(contacts.getId());
         auc.setChannel(au.getChannel());
         auc.setCreatetime(new Date());
         auc.setAppid(au.getAppid());
         auc.setCreater(logined.getId());
         agentUserContactsRes.save(auc);
         return request(super.createRequestPageTempletResponse("redirect:/agent/index.html"));
     }

     @RequestMapping("/calloutcontact/update")
     @Menu(type = "apps", subtype = "calloutcontact")
     public ModelAndView update(HttpServletRequest request, @Valid Contacts contacts) {
         Contacts data = contactsRes.findOne(contacts.getId());
         if (data != null) {
             List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(
                     request, contacts, data, "id", "orgi", "creater", "createtime", "updatetime");    //记录 数据变更 历史
             if (events.size() > 0) {
                 String modifyid = MainUtils.getUUID();
                 Date modifytime = new Date();
                 for (PropertiesEvent event : events) {
                     event.setDataid(contacts.getId());
                     event.setCreater(super.getUser(request).getId());
                     event.setOrgi(super.getOrgi(request));
                     event.setModifyid(modifyid);
                     event.setCreatetime(modifytime);
                     propertiesEventRes.save(event);
                 }
             }

             final User logined = super.getUser(request);

             contacts.setCreater(data.getCreater());
             contacts.setCreatetime(data.getCreatetime());
             contacts.setOrgi(logined.getOrgi());
             contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
             if (StringUtils.isBlank(contacts.getCusbirthday())) {
                 contacts.setCusbirthday(null);
             }
             contactsRes.save(contacts);
         }

         return request(super.createRequestPageTempletResponse("redirect:/agent/index.html"));
     }
 }
