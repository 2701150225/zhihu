package com.fc.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.qq.connect.QQConnectException;
import com.qq.connect.api.OpenID;
import com.qq.connect.api.qzone.UserInfo;
import com.qq.connect.javabeans.AccessToken;
import com.qq.connect.javabeans.qzone.UserInfoBean;
import com.qq.connect.oauth.Oauth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.model.Answer;
import com.fc.model.Collection;
import com.fc.model.PageBean;
import com.fc.model.Question;
import com.fc.model.Topic;
import com.fc.model.User;
import com.fc.service.AnswerService;
import com.fc.service.CollectionService;
import com.fc.service.QuestionService;
import com.fc.service.TopicService;
import com.fc.service.UserService;
import com.fc.util.Response;

@Controller
@RequestMapping("/")
public class UserController {

	private static final String QQ_ACCESS_TOKEN = "accessToken";
	private static final String QQ_OPENID = "openid";

	@Autowired
	private UserService userService;
	@Autowired
	private AnswerService answerService;
	@Autowired
	private QuestionService questionService;
	@Autowired
	private CollectionService collectionService;
	@Autowired
	private TopicService topicService;

	@RequestMapping("/toLogin")
	public String toLogin() {
		return "toLogin";
	}

	@RequestMapping("/register")
	@ResponseBody
	public Response register(String username, String email, String password) {
		Map<String, String> map = userService.register(username, email, password);
		if (map.get("ok") != null) {
			return new Response(0, "系统已经向你的邮箱发送了一封邮件哦，验证后就可以登录啦~");
		} else {
			return new Response(1, "error", map);
		}
	}

	@RequestMapping("/login")
	@ResponseBody
	public Response login(String email, String password, HttpServletResponse response) {

		Map<String, Object> map = userService.login(email, password, response);
		if (map.get("error") == null) {
			return new Response(0, "", map);
		} else {
			return new Response(1, map.get("error").toString());
		}
	}
	
	@RequestMapping("/weiboLogin")
	public String weiboLogin(String code,HttpServletResponse response) throws IOException{
		userService.weiboLogin(code, response);
		return "index";
	}

	@RequestMapping(value = "qqlogin", method = {RequestMethod.GET})
	public String toQQLoginPage(HttpServletRequest request) {
		String authorizeURL = null;
		try {
			authorizeURL = new Oauth().getAuthorizeURL(request);
		} catch (QQConnectException e) {
		}
		authorizeURL = new StringBuffer("redirect:").append(authorizeURL).toString();
		return authorizeURL;
	}



	@RequestMapping(value = "/callback", method = {RequestMethod.GET})
	public String toLoginSuccessPage(HttpServletRequest request, Model model) throws QQConnectException {
		//注意：accessToken，openID是最重要的两个东西，要控制好
		HttpSession session = request.getSession();
		AccessToken accessTokenObj = null;
		String accessToken = null;
		String openID = null;
		try {

			//1.发出第一次请求获取access_token
			//用户已经登陆过
			if (session.getAttribute(QQ_ACCESS_TOKEN) != null && session.getAttribute(QQ_OPENID) != null) {
				accessToken = String.valueOf(session.getAttribute(QQ_ACCESS_TOKEN));
				openID = String.valueOf(session.getAttribute(QQ_OPENID));
			} else {
				//用户第一次登陆
				if ("".equals(accessToken)) {
					//第一次登陆非法请求
				//	logger.warn("[ip:{}]请停止当前非法请求!", IpHelper.getIpAddr(request));
					return "redirect:/fail";
				} else {
					//第一次合法登陆
					//在回调的地址栏中通过Authorization Code获取Access Token，这里工具类进行一步封装了
					accessTokenObj = new Oauth().getAccessTokenByRequest(request);
					accessToken = accessTokenObj.getAccessToken();
					OpenID openIDObj = new OpenID(accessToken);
					//2.发出第二次请求获取openid

					openID = openIDObj.getUserOpenID();
					session.setAttribute("accessToken", accessToken);
					session.setAttribute("openid", openID);
				}
			}
			UserInfo qzoneUserInfo = new UserInfo(accessToken, openID);
			//3.发出第三次请求获取用户信息 userInfoBean

			//返回用户信息
			UserInfoBean userInfoBean = qzoneUserInfo.getUserInfo();
			model.addAttribute("userInfoBean", userInfoBean);

		} catch (QQConnectException e) {
	//		logger.error("跳转到回调地址失败:", e);
			//https://github.com/ChenLaiHong/QQ_Login
			//https://blog.csdn.net/weixin_37762923/article/details/93597061
			return "fail";
		}
		return "index";
	}


	@RequestMapping("/activate")
	public String activate(String code) {
		userService.activate(code);
		return "redirect:/toLogin#activateSuccess";
	}

	@RequestMapping("/logout")
	public String logout(HttpServletRequest request, HttpServletResponse response) {
		userService.logout(request, response);
		return "redirect:/toLogin";
	}

	@RequestMapping("/profile/{userId}")
	public String profile(@PathVariable Integer userId, Integer page, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);
		// 获取回答列表
		PageBean<Answer> pageBean = answerService.listAnswerByUserId(userId, page);
		map.put("pageBean", pageBean);

		model.addAllAttributes(map);
		return "profileAnswer";
	}

	@RequestMapping("/profileQuestion/{userId}")
	public String profileQuestion(@PathVariable Integer userId, Integer page, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);
		// 获取回答列表
		PageBean<Question> pageBean = questionService.listQuestionByUserId(userId, page);
		map.put("pageBean", pageBean);

		model.addAllAttributes(map);
		return "profileQuestion";
	}

	@RequestMapping("/profileCollection/{userId}")
	public String profileCollection(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);
		// 获取收藏夹列表
		List<Collection> collectionList = collectionService.listCreatingCollection(userId);
		map.put("collectionList", collectionList);

		model.addAllAttributes(map);
		return "profileCollection";
	}

	@RequestMapping("/profileFollowCollection/{userId}")
	public String profileFollowCollection(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);
		// 获取收藏夹列表
		List<Collection> collectionList = collectionService.listFollowingCollection(userId);
		map.put("collectionList", collectionList);

		model.addAllAttributes(map);
		return "profileFollowCollection";
	}

	@RequestMapping("/profileFollowPeople/{userId}")
	public String profileFollowPeople(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);

		// 获取关注者列表
		List<User> userList = userService.listFollowingUser(userId);
		map.put("userList", userList);

		model.addAllAttributes(map);
		return "profileFollowPeople";
	}

	@RequestMapping("/profileFollowedPeople/{userId}")
	public String profileFollowedPeople(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);

		// 获取被关注者列表
		List<User> userList = userService.listFollowedUser(userId);
		map.put("userList", userList);

		model.addAllAttributes(map);
		return "profileFollowedPeople";
	}

	@RequestMapping("/profileFollowTopic/{userId}")
	public String profileFollowTopic(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);
		// 获取话题列表
		List<Topic> topicList = topicService.listFollowingTopic(userId);
		map.put("topicList", topicList);
		model.addAllAttributes(map);
		return "profileFollowTopic";
	}

	@RequestMapping("/profileFollowQuestion/{userId}")
	public String profileFollowQuestion(@PathVariable Integer userId, HttpServletRequest request, Model model) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		// 获取用户信息
		Map<String, Object> map = userService.profile(userId, localUserId);

		// 获取问题列表
		List<Question> questionList = questionService.listFollowingQuestion(userId);
		map.put("questionList", questionList);
		model.addAllAttributes(map);
		return "profileFollowQuestion";
	}

	@RequestMapping("/judgePeopleFollowUser")
	@ResponseBody
	public Response judgePeopleFollowUser(Integer userId, HttpServletRequest request) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		boolean status = userService.judgePeopleFollowUser(localUserId, userId);
		return new Response(0, "", status);
	}

	@RequestMapping("/followUser")
	@ResponseBody
	public Response followUser(Integer userId, HttpServletRequest request) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		userService.followUser(localUserId, userId);
		return new Response(0, "");
	}

	@RequestMapping("/unfollowUser")
	@ResponseBody
	public Response unfollowUser(Integer userId, HttpServletRequest request) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		userService.unfollowUser(localUserId, userId);
		return new Response(0, "");
	}
	
	@RequestMapping("/getWeiboUserInfo")
	@ResponseBody
	public Response getWeiboUserInfo(HttpServletRequest request) {
		Integer localUserId = userService.getUserIdFromRedis(request);
		Response response = userService.getWeiboUserInfo(localUserId);
		return response;
	}
	
	

}
