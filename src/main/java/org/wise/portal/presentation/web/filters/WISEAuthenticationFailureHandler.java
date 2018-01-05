/**
 * Copyright (c) 2008-2017 Regents of the University of California (Regents).
 * Created by WISE, Graduate School of Education, University of California, Berkeley.
 *
 * This software is distributed under the GNU General Public License, v3,
 * or (at your option) any later version.
 *
 * Permission is hereby granted, without written agreement and without license
 * or royalty fees, to use, copy, modify, and distribute this software and its
 * documentation for any purpose, provided that the above copyright notice and
 * the following two paragraphs appear in all copies of this software.
 *
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED
 * HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION TO PROVIDE
 * MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 * REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.wise.portal.presentation.web.filters;

import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.transaction.annotation.Transactional;
import org.wise.portal.domain.authentication.MutableUserDetails;
import org.wise.portal.domain.user.User;
import org.wise.portal.service.user.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.transaction.annotation.Transactional;
import org.wise.portal.domain.authentication.MutableUserDetails;
import org.wise.portal.domain.user.User;
import org.wise.portal.service.user.UserService;

/**
 * Handles failed authentication attempts
 * @author Hiroki Terashima
 */
public class WISEAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  @Autowired
  private Properties wiseProperties;

  @Autowired
  private UserService userService;

  public static final Integer recentFailedLoginTimeLimit = 15;

  public static final Integer recentFailedLoginAttemptsLimit = 5;

  private String authenticationFailureUrl;

  /**
   * The user has failed to log in because they either entered
   * an incorrect password or an incorrect ReCaptcha text. We will
   * increment the counter that keeps track of the number of times
   * they have failed to log in within the last 15 minutes.
   * @param request
   * @param response
   */
  @Override
  @Transactional
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException, ServletException {
    String userName = request.getParameter("username");
    if (userName != null) {
      User user = userService.retrieveUserByUsername(userName);
      if (user != null) {
        MutableUserDetails userDetails = user.getUserDetails();
        Date recentFailedLoginTime = userDetails.getRecentFailedLoginTime();
        Date currentTime = new Date();
        Integer numberOfRecentFailedLoginAttempts = 1;
        if (recentFailedLoginTime != null) {
          long timeDifference = currentTime.getTime() - recentFailedLoginTime.getTime();

          /*
           * check if the time difference is less than 15 minutes. if the time difference
           * is less than 15 minutes we will increment the failed attempts counter.
           * if the difference is greater than 15 minutes we will reset the counter.
           */
          if (timeDifference < (recentFailedLoginTimeLimit * 60 * 1000)) {
            numberOfRecentFailedLoginAttempts =
                userDetails.getNumberOfRecentFailedLoginAttempts() + 1;
          }
        }
        userDetails.setNumberOfRecentFailedLoginAttempts(numberOfRecentFailedLoginAttempts);
        userDetails.setRecentFailedLoginTime(currentTime);
        userService.updateUser(user);
      }
    } else if (request.getServletPath().contains("google-login")) {
      String contextPath = request.getContextPath();
      response.sendRedirect(contextPath + "/login/googleUserNotFound");
      return;
    }
    setDefaultFailureUrl(determineFailureUrl(request, response, exception));
    super.onAuthenticationFailure(request, response, exception);
  }

  /**
   * Get the failure url. This function checks if the public and private
   * keys for the captcha have been provided and if the user has failed
   * to log in 5 or more times in the last 15 minutes. If so, it will
   * require the failure url page to display a captcha.
   */
  protected String determineFailureUrl(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException failed) {
    String url = authenticationFailureUrl;
    String failedMessage = failed.getMessage();
    boolean isReCaptchaRequired = isReCaptchaRequired(request, response);
    if (isReCaptchaRequired) {
      if (failedMessage.equals("Please verify that you are not a robot.")) {
        url = authenticationFailureUrl + "&requireCaptcha=true&reCaptchaFailed=true";
      }  else {
        url = authenticationFailureUrl + "&requireCaptcha=true";
      }
    } else {
      url = authenticationFailureUrl;
    }
    return url;
  }

  /**
   * Check if the user is required to answer ReCaptcha. The user is required
   * to answer ReCaptcha if the ReCaptcha keys are valid and the user has
   * previously failed to log in 5 or more times in the last 15 minutes.
   * @param request
   * @param response
   * @return whether the user needs to submit text for ReCaptcha
   */
  public boolean isReCaptchaRequired(HttpServletRequest request, HttpServletResponse response) {
    boolean result = false;
    String reCaptchaPublicKey = wiseProperties.getProperty("recaptcha_public_key");
    String reCaptchaPrivateKey = wiseProperties.getProperty("recaptcha_private_key");

    boolean reCaptchaKeyValid = isReCaptchaKeyValid(reCaptchaPublicKey, reCaptchaPrivateKey);

    if (reCaptchaKeyValid) {
      String userName = request.getParameter("username");
      User user = userService.retrieveUserByUsername(userName);

      /*
       * get the user so we can check if they have been failing to login
       * multiple times recently and if so, we will display a captcha to
       * make sure they are not a bot. the public and private keys must be set in
       * the wise.properties otherwise we will not use captcha at all. we
       * will also check to make sure the captcha keys are valid otherwise
       * we won't use the captcha at all either.
       */
      if (user != null) {
        MutableUserDetails mutableUserDetails = user.getUserDetails();
        if (mutableUserDetails != null) {
          Date currentTime = new Date();
          Date recentFailedLoginTime = mutableUserDetails.getRecentFailedLoginTime();
          if (recentFailedLoginTime != null) {
            long timeDifference = currentTime.getTime() - recentFailedLoginTime.getTime();
            if (timeDifference < (WISEAuthenticationProcessingFilter.recentFailedLoginTimeLimit * 60 * 1000)) {
              Integer numberOfRecentFailedLoginAttempts = mutableUserDetails.getNumberOfRecentFailedLoginAttempts();
              if (numberOfRecentFailedLoginAttempts != null &&
                  numberOfRecentFailedLoginAttempts >= WISEAuthenticationProcessingFilter.recentFailedLoginAttemptsLimit) {
                result = true;
              }
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Check to make sure the public key is valid. We can only check if the public
   * key is valid. If the private key is invalid the admin will have to realize that.
   * We also check to make sure the connection to the ReCaptcha server is working.
   * @param reCaptchaPublicKey the public key
   * @param recaptchaPrivateKey the private key
   * @return whether the ReCaptcha is valid and should be used
   */
  public static boolean isReCaptchaKeyValid(String reCaptchaPublicKey, String recaptchaPrivateKey) {
    boolean isValid = false;

    if (reCaptchaPublicKey != null && recaptchaPrivateKey != null) {
      ReCaptcha c = ReCaptchaFactory.newSecureReCaptcha(reCaptchaPublicKey, recaptchaPrivateKey, false);

      /*
       * get the html that will display the captcha
       * e.g.
       * <script type="text/javascript" src="http://api.recaptcha.net/challenge?k=yourpublickey"></script>
       */
      String recaptchaHtml = c.createRecaptchaHtml(null, null);

      /*
       * try to retrieve the src url by matching everything between the
       * quotes of src=""
       *
       * e.g. http://api.recaptcha.net/challenge?k=yourpublickey
       */
      Pattern pattern = Pattern.compile(".*src=\"(.*)\".*");
      Matcher matcher = pattern.matcher(recaptchaHtml);
      matcher.find();
      String match = matcher.group(1);

      try {
        URL url = new URL(match);
        URLConnection urlConnection = url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

        StringBuffer text = new StringBuffer();
        String inputLine;

        while ((inputLine = in.readLine()) != null) {
          text.append(inputLine);
        }
        in.close();

        String responseText = text.toString();

        /*
         * if the public key was invalid the text returned from the url will
         * look like
         *
         * document.write('Input error: k: Format of site key was invalid\n');
         */
        if(!responseText.contains("Input error")) {
          isValid = true;
        }
      } catch (MalformedURLException e) {
        /*
         * if there was a problem connecting to the server this function will return
         * false so that users can still log in and won't be stuck because the
         * recaptcha server is down.
         */
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return isValid;
  }

  public void setWiseProperties(Properties wiseProperties) {
    this.wiseProperties = wiseProperties;
  }

  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  public void setAuthenticationFailureUrl(String authenticationFailureUrl) {
    this.authenticationFailureUrl = authenticationFailureUrl;
  }
}
