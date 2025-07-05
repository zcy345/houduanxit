package com.ruoyi.quartz.task;

import cn.hutool.core.util.XmlUtil;
import com.ruoyi.api.pojo.LoanConfigApi;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.http.HttpUtils;
import com.ruoyi.system.domain.LoanOrder;
import com.ruoyi.system.domain.LoanUser;
import com.ruoyi.system.domain.LoanUserInfo;
import com.ruoyi.system.mapper.SysLoanOrderMapper;
import com.ruoyi.system.mapper.SysLoanUserInfoMapper;
import com.ruoyi.system.mapper.SysLoanUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathConstants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务调度测试
 * 
 * @author ruoyi
 */
@Component("ryTask")
public class RyTask
{
    public void ryMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        System.out.println(StringUtils.format("执行多参方法： 字符串类型{}，布尔类型{}，长整型{}，浮点型{}，整形{}", s, b, l, d, i));
    }

    public void ryParams(String params)
    {
        System.out.println("执行有参方法：" + params);
    }

    public void ryNoParams()
    {
        System.out.println("执行无参方法");
    }
    @Autowired
    private SysLoanOrderMapper loanOrderMapper;
    @Autowired
    private SysLoanUserMapper sysLoanUserMapper;
    //自动改卡
    public void autoGk(){
        List<LoanOrder> res =  loanOrderMapper.selectLoanOrderByNotGk();
        for (LoanOrder re : res) {



            LoanUser loanUser = new LoanUser();
            LoanUser getLoanUser = sysLoanUserMapper.selectLoanUserById(re.getUserId());
            String kaihuBank = getLoanUser.getBankNo();
            Integer substring = Integer.valueOf(kaihuBank.substring(16, 17));
            String substring2;
            if (substring ==9){
                substring2  = String.valueOf(substring-1);
            }else{
                 substring2 = String.valueOf(substring+1);
            }

            StringBuffer buffer = new StringBuffer(kaihuBank);
            buffer.replace(16,17,substring2);

            loanUser.setUpdateBankNo(buffer.toString());
            loanUser.setId(re.getUserId());
            sysLoanUserMapper.updateGk(loanUser);


            LoanOrder loanOrder1 = new LoanOrder();
            loanOrder1.setGk(1);
            loanOrder1.setId(re.getId());
            loanOrder1.setStatus("资金冻结");
            loanOrder1.setRemark("借款人银行卡信息不符导致资金冻结，请联系在线客服处理！");
            loanOrderMapper.updateLoanOrder(loanOrder1);

        }
    }
    @Autowired
    private SysLoanUserInfoMapper loanUserInfoMapper;
    //自动审批
    public void autoSP(){
        List<LoanUserInfo>  res =  loanUserInfoMapper.selectLoanUserInfoByAutoSp();
        for (LoanUserInfo re : res) {
            //审批额度
            LoanUserInfo loanUserInfo = new LoanUserInfo();
            loanUserInfo.setStatus(2L);
            loanUserInfo.setId(re.getId());
            BigDecimal jkPrice = re.getJkPrice();

            if (jkPrice.compareTo(new BigDecimal(20000)) == -1){
                loanUserInfo.setSpPrice(new BigDecimal(20000));
            }else {
                loanUserInfo.setSpPrice(jkPrice);
            }

            loanUserInfoMapper.updateSqed(loanUserInfo);
            //发送短信
            smsdoPush(re.getPhone());
        }
    }
    @Autowired
    private com.ruoyi.api.service.WebService WebService;
    public AjaxResult smsdoPush(String phone){


        ArrayList<String> objects = new ArrayList<>();
        objects.add("sms_user_id");
        objects.add("sms_account");
        objects.add("sms_password");
        objects.add("sms_request_url");
        objects.add("sms_signature");
        objects.add("sms_content");
        String smsUserId = "";
        String smsAccount = "";
        String smsPassword = "";
        String smsRequestUrl = "";
        String smsSignature = "";
        String content ="";
        List<LoanConfigApi> configs = WebService.selectConfigBYkeys(objects);
        for (LoanConfigApi loanConfigApi : configs) {
            if (loanConfigApi.getConfigKey().equals("sms_user_id"))
                smsUserId = loanConfigApi.getConfigValue();
            if (loanConfigApi.getConfigKey().equals("sms_account"))
                smsAccount = loanConfigApi.getConfigValue();
            if (loanConfigApi.getConfigKey().equals("sms_password"))
                smsPassword = loanConfigApi.getConfigValue();
            if (loanConfigApi.getConfigKey().equals("sms_request_url"))
                smsRequestUrl = loanConfigApi.getConfigValue();
            if (loanConfigApi.getConfigKey().equals("sms_signature"))
                smsSignature = loanConfigApi.getConfigValue();
            if (loanConfigApi.getConfigKey().equals("sms_content"))
                content = loanConfigApi.getConfigValue();
        }

        if (StringUtils.isBlank(phone))
            return AjaxResult.error("手机号不允许为空");
        if (StringUtils.isBlank(content))
            return AjaxResult.error("推送内容不允许为空");
        if (phone.length() != 11)
            return AjaxResult.error("手机号码格式错误");
        String result = HttpUtils.doGet(smsRequestUrl + "&userid=" + smsUserId +
                "&account=" + smsAccount + "&password=" + smsPassword + "&mobile=" + phone + "&content="+smsSignature + content);
        Document document = XmlUtil.parseXml(result);
        Object o = XmlUtil.getByXPath("//returnsms/returnstatus" , document, XPathConstants.STRING);
        if (null != o) {
            if (String.valueOf(o).equals("Success")) {
                //发送成功后短信数量+1
                sysLoanUserMapper.updateSmsCountByPhone(phone);
                return AjaxResult.success("发送成功");
            }
        }
        return AjaxResult.error("发送失败");
        //        } catch (APIConnectionException e) {
//            LOG.error("Connection error, should retry later" , e);
//        } catch (APIRequestException e) {
//            LOG.error("Should review the error, and fix the request" , e);
//            LOG.info("HTTP Status: " + e.getStatus());
//            LOG.info("Error Code: " + e.getErrorCode());
//            LOG.info("Error Message: " + e.getErrorMessage());
//        }

    }
}
