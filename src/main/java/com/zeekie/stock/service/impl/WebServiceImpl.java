package com.zeekie.stock.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sitong.thinker.common.exception.ServiceInvokerException;
import sitong.thinker.common.page.DefaultPage;
import sitong.thinker.common.util.mybatis.BatchMapper;

import com.zeekie.stock.Constants;
import com.zeekie.stock.entity.ClientPercentDO;
import com.zeekie.stock.entity.CurrentOperationWebDO;
import com.zeekie.stock.entity.DayDO;
import com.zeekie.stock.entity.FundAccountDO;
import com.zeekie.stock.entity.MovecashToRefereeDO;
import com.zeekie.stock.entity.OperateAccountDO;
import com.zeekie.stock.entity.OperationInfoDO;
import com.zeekie.stock.entity.OtherFundFlowDO;
import com.zeekie.stock.entity.OwingFeeDO;
import com.zeekie.stock.entity.PayDO;
import com.zeekie.stock.entity.PercentDO;
import com.zeekie.stock.entity.TotalFundDO;
import com.zeekie.stock.entity.UserInfoDO;
import com.zeekie.stock.entity.WithdrawlDO;
import com.zeekie.stock.respository.AcountMapper;
import com.zeekie.stock.respository.TradeMapper;
import com.zeekie.stock.service.AcountService;
import com.zeekie.stock.service.WebService;
import com.zeekie.stock.service.syncTask.SyncHandler;
import com.zeekie.stock.util.StringUtil;
import com.zeekie.stock.web.ClientPage;
import com.zeekie.stock.web.EveningUpPage;
import com.zeekie.stock.web.MoveToRefereePage;
import com.zeekie.stock.web.OperationInfoPage;
import com.zeekie.stock.web.PayPage;
import com.zeekie.stock.web.PercentDOPage;
import com.zeekie.stock.web.TotalFundPage;
import com.zeekie.stock.web.WithdrawlPage;

@Service
@Transactional
public class WebServiceImpl implements WebService {

	Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private AcountMapper account;

	@Autowired
	private TradeMapper trade;

	@Autowired
	private AcountService service;

	@Autowired
	private BatchMapper batchMapper;

	@Autowired
	private SyncHandler handler;

	@Override
	public DefaultPage<WithdrawlDO> getDepositList(WithdrawlPage withdrawlPage)
			throws ServiceInvokerException {
		List<WithdrawlDO> result = new ArrayList<WithdrawlDO>();
		long total = 0;
		try {
			total = account.queryDepositCount(withdrawlPage);
			if (0 != total) {
				result = account.getDepositList(withdrawlPage);
			}
			return new DefaultPage<WithdrawlDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public boolean withdrawlToUser(String id, String nickname, String cash) {
		try {
			account.updateDepositStatus(id);

			account.deductWithdrawCahs(nickname, cash);

			trade.recordFundflow(nickname,
					Constants.TRANS_FROM_WALET_TO_CLIENT, cash, "用户提现");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public DefaultPage<TotalFundDO> getTotalFund(TotalFundPage totalFundPage)
			throws ServiceInvokerException {
		List<TotalFundDO> result = new ArrayList<TotalFundDO>();
		long total = 0;
		try {
			total = account.queryTotalFundCount(totalFundPage.getFundAccount());
			if (0 != total) {
				result = account.getTotalFundList(totalFundPage);
			}
			return new DefaultPage<TotalFundDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public DefaultPage<MovecashToRefereeDO> queryMoveCashToReferee(
			MoveToRefereePage moveToRefereePage) throws ServiceInvokerException {
		List<MovecashToRefereeDO> result = new ArrayList<MovecashToRefereeDO>();
		long total = 0;
		try {
			total = account.queryMoveCashToRefereeCount();
			if (0 != total) {
				result = account.queryMoveCashToReferee(moveToRefereePage);
			}
			return new DefaultPage<MovecashToRefereeDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public boolean addTotalFund(String fund, String fundAccount, String desc,
			String storeType) {
		try {
			account.addTotalFund("1", fund, fundAccount, desc, storeType);
			// 更新历史金额状态为N
			account.updateStatusToN(fundAccount);
			// 更新当前金额状态为Y
			account.updateStatusToY(fundAccount);
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return false;
	}

	@Override
	public DefaultPage<PayDO> getPayList(PayPage payPage)
			throws ServiceInvokerException {
		List<PayDO> result = new ArrayList<PayDO>();
		long total = 0;
		try {
			total = account.getPayListCount();
			if (0 != total) {
				result = account.getPayList(payPage);
			}
			return new DefaultPage<PayDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public boolean payToUs(String id, String nickname, String fund) {
		try {
			if (StringUtils.isNotBlank(id)) {
				account.updatePayStatus(id);
			}

			trade.recharge(nickname, fund);

			trade.recordFundflow(nickname,
					Constants.TRANS_FROM_CLIENT_TO_WALET, fund, "用戶充值");

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public DefaultPage<CurrentOperationWebDO> getEveningUp(
			EveningUpPage eveningUpPage) throws ServiceInvokerException {
		List<CurrentOperationWebDO> result = new ArrayList<CurrentOperationWebDO>();
		long total = 0;
		try {
			total = account.queryCurrentOperationCount(eveningUpPage
					.getNickname());
			if (0 != total) {
				result = account.queryCurrentOperation(eveningUpPage);
			}
			return new DefaultPage<CurrentOperationWebDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public boolean eveningUp(String id, String nickname)
			throws RuntimeException {
		try {
			// 结束操盘,更新操盘为主动结束操盘
			Map<String, String> result = service.endStock(nickname,
					Constants.EVENING_UP);
			if (!StringUtils.equals(Constants.CODE_SUCCESS, result.get("flag"))) {
				return false;
			}
			// 更新操盘为主动结束操盘
			account.update(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException();
		}
		return true;
	}

	@Override
	public void setFeeCalendar(String yearMonth, String days) {
		String[] day = days.split(",");
		List<DayDO> ret = new ArrayList<DayDO>();
		for (String item : day) {
			// int num = Integer.parseInt(item);
			// String eachDay = (num < 10) ? "0" + item : item;
			DayDO dayDO = new DayDO(item, yearMonth);
			ret.add(dayDO);
		}
		batchMapper.batchInsert(TradeMapper.class, "setFeeCalendarBatch", ret);
	}

	@Override
	public String initFeeDays(String month) {

		try {
			List<String> result = trade.initFeeDays(month);
			StringBuilder builder = new StringBuilder();
			for (String day : result) {
				builder.append(day);
				builder.append(",");
			}
			int len = builder.length();
			String days = "";
			if (len >= 1) {
				days = builder.substring(0, len - 1);
			}
			return days;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return "";
	}

	@Override
	public DefaultPage<ClientPercentDO> getClient(ClientPage clientPage)
			throws ServiceInvokerException {
		List<ClientPercentDO> result = new ArrayList<ClientPercentDO>();
		long total = 0;
		try {
			total = account.queryClientCount(clientPage.getNickname());
			if (0 != total) {
				result = account.queryClient(clientPage);
			}
			return new DefaultPage<ClientPercentDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public String getClientById(String id) throws ServiceInvokerException {
		String result = "";
		ClientPercentDO percentDO;
		try {
			percentDO = account.getClientById(id);
			if (null != percentDO) {
				JSONObject jo = JSONObject.fromObject(percentDO,
						Constants.jsonConfig);
				jo.put("managementFeePercent", StringUtil.keepFourDot(percentDO
						.getManagementFeePercent()));
				result = jo.toString();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return result;
	}

	@Override
	public String saveClientInfo(String data) throws ServiceInvokerException {
		try {
			JSONObject jo = JSONObject.fromObject(data);

			JSONObject dbdata = JSONObject.fromObject(getClientById(jo
					.getString("id")));

			String range = StringUtils.trim(jo.getString("range"));
			String stopLine = StringUtils.trim(jo.getString("stopLine"));
			String warnLine = StringUtils.trim(jo.getString("warnLine"));
			String assignPercent = StringUtils.trim(jo
					.getString("assignPercent"));
			String toRefereePacket = StringUtils.trim(StringUtils.trim(jo
					.getString("toRefereePacket")));
			String registerPacket = StringUtils.trim(jo
					.getString("registerPacket"));
			String assignCash = StringUtils.trim(jo.getString("assignCash"));
			String managementFeePercent = StringUtils.trim(jo
					.getString("managementFeePercent"));
			String upLinePercent = StringUtils.trim(jo
					.getString("upLinePercent"));
			String downLinePercent = StringUtils.trim(jo
					.getString("downLinePercent"));

			ClientPercentDO clientPercentDO = new ClientPercentDO();
			if (!StringUtils.equals(range, dbdata.getString("range"))) {
				clientPercentDO.setRange(range);
			}
			if (!StringUtils.equals(stopLine, dbdata.getString("stopLine"))) {
				clientPercentDO.setStopLine(Float.parseFloat(stopLine));
			}
			if (!StringUtils.equals(warnLine, dbdata.getString("warnLine"))) {
				clientPercentDO.setWarnLine(Float.parseFloat(warnLine));
			}
			if (!StringUtils.equals(assignPercent,
					dbdata.getString("assignPercent"))) {
				clientPercentDO.setAssignPercent(Float
						.parseFloat(assignPercent));
			}
			if (!StringUtils.equals(toRefereePacket,
					dbdata.getString("toRefereePacket"))) {
				clientPercentDO.setToRefereePacket(Float
						.parseFloat(toRefereePacket));
			}
			if (!StringUtils.equals(registerPacket,
					dbdata.getString("registerPacket"))) {
				clientPercentDO.setRegisterPacket(Float
						.parseFloat(registerPacket));
			}
			if (!StringUtils.equals(assignCash, dbdata.getString("assignCash"))) {
				clientPercentDO.setAssignCash(Float.parseFloat(assignCash));
			}
			if (!StringUtils.equals(managementFeePercent,
					dbdata.getString("managementFeePercent"))) {
				clientPercentDO.setManagementFeePercent(Float
						.parseFloat(managementFeePercent));
			}
			if (!StringUtils.equals(upLinePercent,
					dbdata.getString("upLinePercent"))) {
				clientPercentDO.setUpLinePercent(Float
						.parseFloat(upLinePercent));
			}
			if (!StringUtils.equals(downLinePercent,
					dbdata.getString("downLinePercent"))) {
				clientPercentDO.setDownLinePercent(Float
						.parseFloat(downLinePercent));
			}
			clientPercentDO.setId(jo.getString("id"));
			account.saveClientInfo(clientPercentDO);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return "1";
	}

	@Override
	public String getFundAccount(String status) throws ServiceInvokerException {
		String result = "";
		List<FundAccountDO> fundAccount;
		try {
			fundAccount = account.getFundAccount(status);
			if (null != fundAccount) {
				JSONArray jo = JSONArray.fromObject(fundAccount);
				result = jo.toString();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return result;
	}

	@Override
	public String saveFundAccount(String data) throws ServiceInvokerException {
		// JSONArray ja = JSONArray.fromObject(data);
		JSONObject jo = JSONObject.fromObject(data);
		String type = jo.getString("type");
		String managerAccountId = jo.getString("id");
		try {
			account.updateFundAccountStatus(type, managerAccountId);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return "1";
		// try {
		// if (jo.isEmpty()) {
		// account.updateAllStatusToOne();
		// } else {
		// StringBuffer sb = new StringBuffer();
		// for (int i = 0; i < ja.size(); i++) {
		// JSONObject jo = ja.getJSONObject(i);
		// sb.append("'");
		// sb.append(jo.getString("id"));
		// sb.append("',");
		// }
		// String accounts = sb.toString();
		// accounts = accounts.substring(0, accounts.lastIndexOf(","));
		// account.updateStatusToZero(accounts);
		// account.updateStatusToOne(accounts);
		// }
		// } catch (Exception e) {
		// log.error(e.getMessage(), e);
		// throw new ServiceInvokerException(e.getMessage());
		// }

	}

	@Override
	public DefaultPage<OwingFeeDO> getOwingFee(ClientPage clientPage)
			throws ServiceInvokerException {
		List<OwingFeeDO> result = new ArrayList<OwingFeeDO>();
		long total = 0;
		try {
			total = account.queryOwingFeeCount(clientPage.getNickname());
			if (0 != total) {
				result = account.queryOwingFee(clientPage);
			}
			return new DefaultPage<OwingFeeDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public void openOrCloseApp(String param) {
		handler.handleJob(Constants.TYPE_JOB_CONTROL_APP, param);
	}

	@Override
	public DefaultPage<PercentDO> caculatePercent(PercentDOPage page)
			throws ServiceInvokerException {
		List<PercentDO> result = new ArrayList<PercentDO>();
		long total = 0;
		try {
			total = account.queryTotal(page.getAssetName());
			if (0 != total) {
				result = account.queryList(page);

				List<OperateAccountDO> operateAccountDO = account
						.queryOperateAccountByFlag();

				for (PercentDO item : result) {
					for (OperateAccountDO each : operateAccountDO) {
						if (StringUtils.equals(each.getFundAccount(),
								item.getFundAcount())) {
							String num = each.getNums();
							if (StringUtils.equals(each.getFlag(), "1")) {
								item.setUseCount(num);
							} else {
								item.setLeaveCount(num);
							}
						}
						if (StringUtils.isNotBlank(item.getUseCount())
								&& StringUtils.isNotBlank(item.getLeaveCount())) {
							break;
						}
					}
				}
			}
			return new DefaultPage<PercentDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public DefaultPage<UserInfoDO> getClientInfo(ClientPage clientPage)
			throws ServiceInvokerException {
		List<UserInfoDO> result = new ArrayList<UserInfoDO>();
		long total = 0;
		try {
			total = account.queryClientInfoCount(clientPage.getNickname());
			if (0 != total) {
				result = account.queryClientInfo(clientPage);
			}
			return new DefaultPage<UserInfoDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public DefaultPage<OtherFundFlowDO> getFundFlowInfo(ClientPage clientPage)
			throws ServiceInvokerException {
		List<OtherFundFlowDO> result = new ArrayList<OtherFundFlowDO>();
		long total = 0;
		try {
			total = account.queryFundFlowInfoCount(clientPage.getNickname());
			if (0 != total) {
				result = account.queryFundFlowInfo(clientPage);
				convertBussinessType(result);
			}
			return new DefaultPage<OtherFundFlowDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	private void convertBussinessType(List<OtherFundFlowDO> result) {
		for (int i = 0; i < result.size(); i++) {
			OtherFundFlowDO flowDO = result.get(i);
			String type = StringUtils.trim(flowDO.getBussniessType());
			Float fund = flowDO.getFund();
			String desc = flowDO.getDesc();
			if (StringUtils.equals(type, Constants.TIPS_RETURN_GURANTEE_CASH)) {
				flowDO.setBussniessType("返还平仓后保证金");
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("10", type)) {
				flowDO.setBussniessType("支付保证金");
				flowDO.setFundStr("-" + fund);
			} else if (StringUtils.equals("20", type)) {
				flowDO.setBussniessType("获取配资");
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("30", type)) {
				flowDO.setBussniessType("扣除服务费");
				flowDO.setFundStr("-" + fund);
			} else if (StringUtils.equals("40", type)) {
				flowDO.setBussniessType("用户充值");
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("50", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("-" + fund);
			} else if (StringUtils.equals("60", type)) {
				boolean flag = StringUtils.startsWith("" + fund, "-");
				flowDO.setBussniessType(flag ? "亏损(扣除保证金)" : "返还保证金");
				flowDO.setFundStr(flag ? "" + fund : "+" + fund);
			} else if (StringUtils.equals("80", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("90", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("100", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("-" + fund);
			} else if (StringUtils.equals("110", type)) {//
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("120", type)) {//
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("130", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			} else if (StringUtils.equals("140", type)) {
				flowDO.setBussniessType(desc);
				flowDO.setFundStr("+" + fund);
			}
		}

	}

	@Override
	public DefaultPage<OperationInfoDO> getOperationInfo(
			OperationInfoPage infoPage) throws ServiceInvokerException {
		List<OperationInfoDO> result = new ArrayList<OperationInfoDO>();
		long total = 0;
		try {
			total = account.queryOperationInfoCount(infoPage.getNickname(),
					infoPage.getRange());
			if (0 != total) {
				result = account.queryOperationInfo(infoPage);
			}
			return new DefaultPage<OperationInfoDO>(total, result);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e);
		}
	}

	@Override
	public String sendRedPacket(String data) throws ServiceInvokerException {
		JSONObject jo = JSONObject.fromObject(data);
		String nickname = jo.getString("nickname");
		String telephone = jo.getString("telephone");
		String fund = jo.getString("fund");
		String message = jo.getString("message");
		try {
			if (StringUtils.isNotBlank(fund)) {
				account.moveProfitToUserWallet(nickname, fund);

				trade.recordFundflow(nickname, Constants.SEND_RED_PACKET, fund,
						"平台财务优化");
			}

			if (StringUtils.isNotBlank(message)) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("nickname", nickname);
				map.put("message", message);
				map.put("telephone", telephone);
				handler.handleOtherJob(Constants.TYPE_JOB_REDPACKET_NOTICE, map);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return Constants.CODE_SUCCESS;
	}

	@Override
	public String sendMsgToAll(String data) throws ServiceInvokerException {
		JSONObject jo = JSONObject.fromObject(data);
		String message = jo.getString("message");
		try {
			if (StringUtils.isNotBlank(message)) {
				if (StringUtils.isNotBlank(message)) {
					handler.handleJob(Constants.TYPE_JOB_SENDMSG_NOTICE,
							message);
				}
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ServiceInvokerException(e.getMessage());
		}
		return Constants.CODE_SUCCESS;
	}

	@Override
	public boolean undoWithDrawal(String id, String nickname, String cash)
			throws ServiceInvokerException {
		try {
			account.deleteWithdral(id);

			account.updateWithdrawCash(nickname, cash);

			trade.recordFundflow(nickname, Constants.SEND_UNDO_WITHDRAWL, cash,
					"用户撤销提现");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public void updateReceiptStatus(Map<String, String> param)
			throws ServiceInvokerException {
		String nickname = "";
		try {
			nickname = account.queryNickname(param.get("userId"));
		} catch (Exception e) {
			throw new ServiceInvokerException(e.getMessage());
		}
		param.put("nickname", nickname);
		trade.updateReceiptStatus(param);
	}
}
