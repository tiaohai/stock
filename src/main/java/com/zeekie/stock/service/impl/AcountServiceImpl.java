package com.zeekie.stock.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeekie.stock.Constants;
import com.zeekie.stock.entity.AccountDO;
import com.zeekie.stock.entity.BankInfoDO;
import com.zeekie.stock.entity.BasicInfoDO;
import com.zeekie.stock.entity.BindBankDO;
import com.zeekie.stock.entity.CashDO;
import com.zeekie.stock.entity.CurrentAccountDO;
import com.zeekie.stock.entity.DebtDO;
import com.zeekie.stock.entity.EndStockCashDO;
import com.zeekie.stock.entity.FundFlowDO;
import com.zeekie.stock.entity.IdentifyDO;
import com.zeekie.stock.entity.RedPacketDO;
import com.zeekie.stock.entity.RedpacketAndBalanceDO;
import com.zeekie.stock.entity.UserDO;
import com.zeekie.stock.entity.WithdrawPageDO;
import com.zeekie.stock.enums.Fund;
import com.zeekie.stock.respository.AcountMapper;
import com.zeekie.stock.respository.TradeMapper;
import com.zeekie.stock.service.AcountService;
import com.zeekie.stock.service.homes.StockAssetMove;
import com.zeekie.stock.service.homes.StockCapitalChanges;
import com.zeekie.stock.service.homes.StockModifyUserName;
import com.zeekie.stock.service.syncTask.SyncHandler;
import com.zeekie.stock.util.ApiUtils;
import com.zeekie.stock.util.StringUtil;

@Service
@Transactional
public class AcountServiceImpl extends BaseImpl implements AcountService {

	static Logger log = LoggerFactory.getLogger(AcountServiceImpl.class);

	@Autowired
	private AcountMapper acounter;

	@Autowired
	private TradeMapper trade;

	@Autowired
	@Value("${stock_manager_phone}")
	private String stock_manager_phone;

	@Autowired
	@Value("${templateId_deposit_notice}")
	private String templateId_deposit_notice;

	@Autowired
	@Value("${stock_spreed_page_path}")
	private String stock_spreed_page_path;

	@Autowired
	@Value("${func_am_stock_current_qry}")
	private String Fn_stock_current;// 769203-股票资产查询

	@Autowired
	@Value("${func_am_asset_move}")
	private String Fn_asset_move;

	@Autowired
	@Value("${func_am_change_asset_info}")
	private String fn_change_assetName;// 769952-修改homse用户名

	@Autowired
	private SyncHandler handler;

	@Override
	public boolean indentify(String nickname, String truename, String idCard)
			throws RuntimeException {
		// TODO:indentify can't be finish. do it after i am happy .
		try {

			if (log.isDebugEnabled()) {
				log.debug("用户" + nickname + "开始认证...");
			}

			if (StringUtils.equals(Constants.CODE_SUCCESS,
					acounter.queryIdentifyFlag(nickname))) {
				if (log.isDebugEnabled()) {
					log.debug("用户" + nickname + "已经认证,直接返回true");
				}
				return true;
			}

			// 1、实名认证
			acounter.insertIdentify(nickname, truename,
					StringUtils.upperCase(idCard));

			// 2、身份认证,平台给注册的人的钱
			String plat_money = acounter.getPlatRedPacketToRegister(nickname);
			acounter.moveRedPacketToReferee(nickname, plat_money);

			// 2、1记录流水 平台红包
			trade.recordFundflow(nickname, Fund.PLAT_RED_PACKET.getType(),
					plat_money,
					Fund.getDesc(nickname, Fund.PLAT_RED_PACKET.getType()));

			// 3、如果有推荐人，给推荐人红包
			String referee = acounter.queryReferee(nickname);
			if (StringUtils.isNotBlank(referee)) {

				RedpacketAndBalanceDO packet = acounter
						.getRefereeRedPacket(referee);

				if (packet == null) {
					if (log.isDebugEnabled()) {
						log.debug("推荐人" + referee + "未设置红包");
					}
					// 如果未设置红包，那么给推荐人红包和积分
					caculatePacketAndIntegral(nickname, referee);

					Map<String, String> param = new HashMap<String, String>();
					param.put("nickname", nickname);
					param.put("referee", referee);
					param.put("packet", "0.0");
					param.put("flag", "zero");
					handler.handleOtherJob(Constants.TYPE_JOB_TO_REFEREE, param);
					return true;
				}

				Float refereeRedPacket = packet.getRedPacket();
				Float value = packet.getValue();

				// 钱包余额不足以支付推荐人设置的红包
				if (value < 0f) {
					if (log.isDebugEnabled()) {
						log.debug("推荐人" + referee + "所剩余额已经不足以支付红包，故用户"
								+ nickname + "不设置该推荐人");
					}
					acounter.updateRefereeIsNull(nickname);
					acounter.updateRefereeRadioIsNull(nickname);
					Map<String, String> param = new HashMap<String, String>();
					param.put("nickname", nickname);
					param.put("referee", referee);
					param.put("packet", refereeRedPacket + "");
					param.put("flag", "insufficient");
					handler.handleOtherJob(Constants.TYPE_JOB_TO_REFEREE, param);
				} else {
					caculatePacketAndIntegral(nickname, referee);

					String type = "";
					// 途径人设置红包大于0，才开始分红包
					if (refereeRedPacket > 0f) {

						// 3、1 减去推荐人指定的红包，记录流水
						trade.deductGuaranteeCash(refereeRedPacket + "",
								referee);

						type = Fund.REFEREE_PRAISE.getType();
						trade.recordFundflow(referee, type, refereeRedPacket
								+ "", Fund.getDesc(nickname, type));

						if (log.isDebugEnabled()) {
							log.debug("用户[" + referee + "]指定了一个红包["
									+ refereeRedPacket + "" + "]，分给注册人["
									+ nickname + "],从钱包扣除该笔金额："
									+ refereeRedPacket + "");
						}

						// 3、2将推荐人的红包发给注册人，记录流水
						acounter.moveRedPacketToReferee(nickname,
								refereeRedPacket + "");
						type = Fund.CLIENT_PRIASE.getType();
						trade.recordFundflow(nickname, type, refereeRedPacket
								+ "", Fund.getDesc(referee, type));

						if (log.isDebugEnabled()) {
							log.debug("用户[" + nickname + "]收到了推荐人[" + referee
									+ "]的一个红包[" + refereeRedPacket + "" + "]");
						}
					}

					Map<String, String> param = new HashMap<String, String>();
					param.put("nickname", nickname);
					param.put("referee", referee);
					param.put("packet", refereeRedPacket + "");
					param.put("flag", "enough");
					handler.handleOtherJob(Constants.TYPE_JOB_TO_REFEREE, param);

				}

			}
			if (log.isDebugEnabled()) {
				log.debug("用户" + nickname + "认证成功，认证结束！！！");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
		return true;
	}

	private void caculatePacketAndIntegral(String nickname, String referee)
			throws Exception {
		// 3、2 给推荐人算积分
		acounter.recordIntegral(nickname);
		String platRedPacketToreferee = acounter
				.getPlatRedPacketToReferee(referee);
		String type = "";
		if (StringUtils.isNotBlank(platRedPacketToreferee)) {
			acounter.moveRedPacketToReferee(referee, platRedPacketToreferee);

			// 3、3 给推荐人分红包 5
			type = Fund.REFEREE_PACKET.getType();
			trade.recordFundflow(referee, type, platRedPacketToreferee,
					Fund.getDesc(nickname, type));

			if (log.isDebugEnabled()) {
				log.debug("用户[" + nickname + "]拥有推荐人[" + referee
						+ "]，平台给推荐人红包：" + platRedPacketToreferee + "元");
			}
		}
	}

	@Override
	public boolean bindCreditCard(String userId, String telephone, String bank,
			String number, String bankCode, String code) {
		try {
			acounter.bindCreditCard(userId, telephone, bank, number, bankCode,
					code);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public boolean setDepositPwd(String nickname, String telephone,
			String depositPwd) {
		try {
			acounter.setDepositPwd(nickname, telephone, depositPwd);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public JSONArray getFundFlow(String nickname, String offset) {
		JSONArray jo = new JSONArray();
		try {
			List<FundFlowDO> flow = acounter.getFundFlow(nickname, offset);
			if (null != flow) {
				jo = JSONArray.fromObject(flow, Constants.jsonConfig);
				covertToCHA(jo);
			}
			if (log.isDebugEnabled()) {
				log.debug("用户[" + nickname + "]获取资金流水，传递偏移量：[" + offset
						+ "] 返回的结果：" + jo);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return jo;
	}

	private void covertToCHA(JSONArray flow) {
		for (int i = 0; i < flow.size(); i++) {
			JSONObject jo = flow.getJSONObject(i);
			String type = StringUtils.trim(jo.getString("transactionType"));
			String fund = jo.getString("fund");
			String desc = jo.getString("desc");
			if (StringUtils.equals(type, Constants.TIPS_RETURN_GURANTEE_CASH)) {
				jo.put("transactionType", "返还平仓后保证金");
				jo.put("fund", fund);
			} else if (StringUtils.equals("10", type)) {
				jo.put("transactionType", "支付保证金");
				jo.put("fund", "-" + fund);
			} else if (StringUtils.equals("20", type)) {
				jo.put("transactionType", "获取配资");
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("30", type)) {
				jo.put("transactionType", "扣除服务费");
				jo.put("fund", "-" + fund);
			} else if (StringUtils.equals("40", type)) {
				jo.put("transactionType", "用户充值");
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("50", type)) {
				jo.put("transactionType", desc);
				jo.put("fund", "-" + fund);
			} else if (StringUtils.equals("60", type)) {
				boolean flag = StringUtils.startsWith(fund, "-");
				jo.put("transactionType", flag ? "亏损(扣除保证金)" : "返还保证金");
				jo.put("fund", flag ? fund : "+" + fund);
			} else if (StringUtils.equals("80", type)) {
				jo.put("transactionType", desc);
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("90", type)) {
				jo.put("transactionType", desc);// 有人拿着你的ID去推广，平台奖励5元
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("100", type)) {
				jo.put("transactionType", desc);
				jo.put("fund", "-" + fund);
			} else if (StringUtils.equals("110", type)) {//
				jo.put("transactionType", desc);
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("120", type)) {//
				jo.put("transactionType", desc);
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("130", type)) {//
				jo.put("transactionType", desc);
				jo.put("fund", "+" + fund);
			} else if (StringUtils.equals("140", type)) {//
				jo.put("transactionType", desc);
				jo.put("fund", "+" + fund);
			}
		}
	}

	@Override
	public Map<String, String> getCurrentAccount(String nickname) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			CurrentAccountDO account = acounter.getCurrentAccount(nickname);
			if (null != account) {
				String balance = account.getBalance() + "";
				map.put("balance", (account.getBalance() == null) ? "0.00"
						: balance);
				String guaranteeCash = account.getGuaranteeCash() + "";
				map.put("guaranteeCash",
						(account.getGuaranteeCash() == null) ? "0.00"
								: guaranteeCash);
				String capital = account.getStockCapital() + "";
				map.put("stockCapital",
						(account.getStockCapital() == null) ? "0.00" : capital);
				String freezeCash = account.getFreezeCash() + "";
				map.put("freezeCash",
						(account.getFreezeCash() == null) ? "0.00" : freezeCash);
				String fee = account.getFee() + "";
				map.put("fee", (null == account.getFee()) ? "" : fee);
			} else {
				map.put("balance", "0.00");
				map.put("guaranteeCash", "0.00");
				map.put("stockCapital", "0.00");
				map.put("freezeCash", "0.00");
				map.put("fee", "");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return map;
	}

	@Override
	public boolean clientRecharge(String nickname, String fund,
			String payAccount) {
		try {
			// 充值
			// trade.recharge(nickname, fund);
			//
			// trade.recordFundflow(nickname,
			// Constants.TRANS_FROM_CLIENT_TO_WALET, fund);
			trade.insertRecord(nickname, payAccount, fund);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public Map<String, String> withdraw(String nickname, String fund,
			String depositPwd, String openBank) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("flag", "0");
		String msg = "";
		try {
			if (!StringUtils.equals("1", acounter.isIdentify(nickname))) {
				msg = "亲，你未认证身份证号或者没有绑定银行卡，请核对信息后再进行操作，谢谢";
				map.put("msg", msg);
				map.put("flag", "4");
				return map;
			} else {

				if (!StringUtils
						.equals("1", acounter.queryDepositPwd(nickname))) {
					msg = "提现密码未设置";
					map.put("msg", msg);
					map.put("flag", "5");
					return map;
				}

				if (!StringUtils.equals("1",
						acounter.checkDepositPwd(nickname, depositPwd))) {
					msg = "提现密码输入错误，请重新输入,或则联系管理员!";
					map.put("msg", msg);
					map.put("flag", "2");
					return map;
				}

				// 检查余额是否充足
				if (!StringUtils.equals("1",
						acounter.checkBalance(nickname, fund))) {
					msg = "账户余额不足，无法提现！";
					map.put("msg", msg);
					map.put("flag", "3");
					return map;
				}

				msg = "提现审核中！";
				acounter.withdraw(nickname, fund);
				// 设置冻结金额
				acounter.saveFreezeCash(nickname, fund);
				// 更新开户行
				acounter.updateOpenBank(nickname, openBank);
				// 发短信通知管理员有人提款
				ApiUtils.send(Constants.MODEL_DEPOSIT_SQ_FN,
						stock_manager_phone);
				map.put("flag", "1");
				map.put("msg", msg);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			map.put("msg", "服务异常");
			return map;
		}
		return map;
	}

	@Override
	public Map<String, String> endStock(String nickname, String flag)
			throws RuntimeException {
		Map<String, String> result = new HashMap<String, String>();
		result.put("flag", Constants.CODE_FAILURE);
		String msg = "成功";

		try {// 1、判断是否已经结束操盘，即判断股票市值是否为0；
			if (StringUtils.equals(Constants.CODE_SUCCESS,
					acounter.operationIsEnded(nickname))) {

				if (log.isDebugEnabled()) {
					log.debug("用户" + nickname + "开始结束操盘");
				}

				// 1、1结束操盘将资金划到HOMES
				if (!moveCashForEndStock(nickname)) {
					result.put("msg", "【9001】结束操盘时资金划转失败！");
					return result;
				}
				// 1、2计算我们的钱和用户的钱
				EndStockCashDO cashDO = acounter.queryUserLastCash(nickname);

				// 1、3判断用户是否有欠款
				String userCash = StringUtil.keepThreeDot(cashDO.getUserCash());

				// 1、4如果有欠款，就计入冻结金额
				if (cashDO.getUserCash() < 0) {
					acounter.updateDebt(userCash, nickname);
					result.put("debt", userCash);
				} else {
					result.put("debt", "0");
					// 2、
					// 把获取的利润划到账户钱包,特别注意，如果只有利润大于0才加到用户余额中，不然会直接扣掉用户钱。但是又回记账debt，造成重复扣款
					acounter.moveProfitToUserWallet(nickname, userCash);
					// 2、1记录流水
					String type = StringUtils
							.equals(Constants.EVENING_UP, flag) ? Constants.TIPS_RETURN_GURANTEE_CASH
							: Constants.TRANS_FROM_HOMES_TO_CLIENT;
					trade.recordFundflow(nickname, type, userCash, "");
				}

				String assginCash = StringUtil.keepThreeDot(cashDO
						.getAssginCash());

				// 3、把我们的配资的钱划到我们的总资金
				acounter.addTotalFund("0", assginCash, cashDO.getFundAccount(),
						"从HOMES划回配资的钱", "recharge");
				// 更新历史金额状态为N
				acounter.updateStatusToN(cashDO.getFundAccount());
				// 更新当前金额状态为Y
				acounter.updateStatusToY(cashDO.getFundAccount());

				// 4、计算推荐人的收益，如果该用户有推荐人。计算规则：每笔服务费的乘以一个百分比
				trade.caculateRefereeIncome(nickname);

				// 5、更新操盘为历史操盘
				trade.currentOperationOver(nickname);

				trade.updateCurrentAccountMayUse(nickname);

				// 6、设置当前操盘账户为历史账号，用户重新操盘需要重新获取操盘账号
				trade.setCurrentOpertionAccountIsHistory(nickname);

				result.put("flag", Constants.CODE_SUCCESS);
				result.put("msg", msg);

				if (log.isDebugEnabled()) {
					log.debug("用户" + nickname + "结束操盘成功");
				}

			} else {
				msg = "您还有未卖出的股票，请平仓后再结束操盘！";
				result.put("flag", Constants.CODE_FAILURE);
				result.put("msg", msg);
			}
		} catch (Exception e) {
			log.error("结束操盘失败!!!");
			log.error(e.getMessage(), e);
			throw new RuntimeException();
		}
		return result;
	}

	private boolean modifyUserName(String fundAccount, String combineId)
			throws Exception {
		StockModifyUserName user = new StockModifyUserName(fundAccount,
				combineId, "空闲用户");
		user.callHomes(fn_change_assetName);
		return user.visitSuccess(fn_change_assetName);
	}

	private boolean moveCashForEndStock(String nickname) throws Exception {
		AccountDO client = acounter.getAccount(nickname);
		String fundAccount = client.getFundAccount();
		String combineId = client.getCombineId();
		StockCapitalChanges changes = new StockCapitalChanges(fundAccount,
				combineId);
		if (log.isDebugEnabled()) {
			log.debug("开始访问homes做资金划转，以便结束操盘");
		}
		changes.callHomes(Fn_stock_current);
		String currentCash = changes.getDataSet().getDataset(0)
				.getString("current_cash");
		if (StringUtils.isNotBlank(currentCash)
				|| !StringUtils.equals("0", currentCash)) {
			log.error("操盘账号：" + client.getTradeAcount()
					+ " 仍然有有资金在HOMES中，下面将资金划转到主单元!!!");
			StockAssetMove assetMove = new StockAssetMove(fundAccount,
					combineId, client.getManagerCombineId(), currentCash);
			assetMove.callHomes(Fn_asset_move);
			if (assetMove.visitSuccess(Fn_stock_current)) {
				log.error("将操盘账号为：" + client.getTradeAcount() + " 的资金["
						+ currentCash + "]划转到主单元!!!");
			} else {
				return false;
			}
		}

		if (modifyUserName(fundAccount, combineId)) {
			if (log.isDebugEnabled()) {
				log.debug("修改用户名称为【空闲用户】");
			}
		} else {
			log.warn("修改用户名称为【空闲用户】失败，调用HOMES发生异常.但是主程序继续进行！！！");
		}

		if (log.isDebugEnabled()) {
			log.debug("结束操盘操作，访问homes成功结束！");

		}
		return true;
	}

	@Override
	public Map<String, String> enterSpreadPage(String nickname) {

		Map<String, String> map = new HashMap<String, String>();

		try {
			RedPacketDO packketDO = acounter.getRedPacket(nickname);
			if (null != packketDO) {
				map.put("assignRefereeRedPacket",
						packketDO.getAssignRefereeRedPacket());
				map.put("assignRegisterRedPacket",
						StringUtils.defaultIfBlank(
								packketDO.getAssignRegisterRedPacket(), "0.0"));
				map.put("assignRefereeDrawPercent", StringUtil
						.FloatToPercentum(packketDO
								.getAssignRefereeDrawPercent()));
				map.put("spreedPath",
						stock_spreed_page_path + packketDO.getUserId());
				map.put("refereeId", packketDO.getUserId());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return map;
	}

	@Override
	public boolean spread(String refereeId, String redPacket) {
		// 插入推广人指定的红包表
		try {
			Long id = trade.getSeqId("SEQ_REFEREE_RED_PACKET_ID.Nextval");
			if (StringUtils.isNotBlank(redPacket)) {
				acounter.insertRefereeRedPacket(id, refereeId, redPacket);
			}
			if (log.isDebugEnabled()) {
				log.debug("用户：" + refereeId + "，设置红包：" + redPacket);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public Map<String, String> withdrawPageEnter(String nickname) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("flag", "0");
		try {
			WithdrawPageDO pageDO = acounter.withdrawPageEnter(nickname);
			if (null != pageDO) {
				map.put("flag", "1");
				map.put("bank", pageDO.getBank());
				map.put("number", pageDO.getNumber());
				map.put("privateRule", pageDO.getPrivateRule());
				map.put("rule", pageDO.getRule());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return map;
		}
		return map;
	}

	@Override
	public String checkDepoist(String userId, String depositPwd) {
		try {
			if (StringUtils.isBlank(acounter.checkDepoist(userId, depositPwd))) {
				return "0";
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return "1";
	}

	@Override
	public String deductDebt(String nickname) {

		try {
			CashDO cashDO = acounter.selectCash(nickname);
			if (cashDO.getResidueCash() > 0) {
				acounter.deductDebt(cashDO.getResidueCash(), nickname);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Constants.CODE_FAILURE;
		}
		return Constants.CODE_SUCCESS;
	}

	@Override
	public Map<String, String> getWallet(String nickname) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			DebtDO debtDO = acounter.getWallet(nickname);
			if (null != debtDO) {
				map.put("balance", StringUtil.keepThreeDot(debtDO.getBalance()));
				map.put("guaranteeCash",
						StringUtil.keepThreeDot(debtDO.getGuaranteeCash()));
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return map;
	}

	@Override
	public JSONObject getUserInfo(String userId) {
		try {
			UserDO user = acounter.getUserInfo(userId);
			if (null != user) {
				return JSONObject.fromObject(user);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	@Override
	public JSONObject getBankInfo(String userId) {
		try {
			BankInfoDO bankInfo = acounter.getBankInfo(userId);
			if (null != bankInfo) {
				return JSONObject.fromObject(bankInfo);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	@Override
	public String modifyDepositPwd(String userId, String depositPwd,
			String telephone, String verifyCode) {
		if (!validVerifyCode(telephone, verifyCode,
				Constants.CODE_VERIFYCODE_SOURCE_DEPOSIT_UPDATE)) {
			return Constants.CODE_ERROR_VERIFYCODE;
		} else {
			try {
				acounter.updateDepositePwd(userId, depositPwd);
			} catch (Exception e) {
				return Constants.CODE_FAILURE;
			}
		}
		return Constants.CODE_SUCCESS;
	}

	@Override
	public Map<String, String> getId(String userId) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			IdentifyDO indetifyDO = acounter.queryIdentifyInfo(userId);
			if (null != indetifyDO) {
				map.put("trueName", indetifyDO.getTrueName());
				map.put("idCard", indetifyDO.getIdCard());
				map.put("indentifyDate", indetifyDO.getIndentifyDate());
				map.put("depositPwd", indetifyDO.getDepositPwd());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return map;
	}

	@Override
	public Map<String, String> getBindCreditCard(String userId) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			BindBankDO bindBankDO = acounter.getBindCreditCard(userId);
			if (null != bindBankDO) {
				map.put("bank", bindBankDO.getBank());
				map.put("cardNo", bindBankDO.getCardNo());
				map.put("openBank", bindBankDO.getOpenBank());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return map;
	}

	@Override
	public JSONObject getBankLimitation(String code) {
		try {
			BankInfoDO bankInfo = acounter.getBankLimitation(code);
			if (null != bankInfo) {
				return JSONObject.fromObject(bankInfo);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	@Override
	public JSONObject getBasicInfo(String userId) {
		try {
			BasicInfoDO basicinfoDO = acounter.getBasicInfo(userId);
			basicinfoDO.setAppStatus(Constants.HOMES_STATUS);
			return JSONObject.fromObject(basicinfoDO);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}
}
