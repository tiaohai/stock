package com.zeekie.stock.entity;

public class UserInfoDO {

	private String nickname;

	private String phone;

	private String trueName;

	private Float balance;

	private Float actualFund;

	private String upLine;

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @param nickname
	 *            the nickname to set
	 */
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	/**
	 * @return the phone
	 */
	public String getPhone() {
		return phone;
	}

	/**
	 * @param phone
	 *            the phone to set
	 */
	public void setPhone(String phone) {
		this.phone = phone;
	}

	/**
	 * @return the trueName
	 */
	public String getTrueName() {
		return trueName;
	}

	/**
	 * @param trueName
	 *            the trueName to set
	 */
	public void setTrueName(String trueName) {
		this.trueName = trueName;
	}

	/**
	 * @return the balance
	 */
	public Float getBalance() {
		return balance;
	}

	/**
	 * @param balance
	 *            the balance to set
	 */
	public void setBalance(Float balance) {
		this.balance = balance;
	}

	/**
	 * @return the actualFund
	 */
	public Float getActualFund() {
		return actualFund;
	}

	/**
	 * @param actualFund
	 *            the actualFund to set
	 */
	public void setActualFund(Float actualFund) {
		this.actualFund = actualFund;
	}

	/**
	 * @return the upLine
	 */
	public String getUpLine() {
		return upLine;
	}

	/**
	 * @param upLine
	 *            the upLine to set
	 */
	public void setUpLine(String upLine) {
		this.upLine = upLine;
	}

	public UserInfoDO() {
		// TODO Auto-generated constructor stub
	}

}
