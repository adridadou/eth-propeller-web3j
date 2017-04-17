package org.adridadou.ethereum.rpc;

import org.adridadou.ethereum.propeller.Crypto;
import org.adridadou.ethereum.propeller.EthereumBackend;
import org.adridadou.ethereum.propeller.event.EthereumEventHandler;
import org.adridadou.ethereum.propeller.values.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.utils.Numeric;

/**
 * Created by davidroon on 20.01.17.
 * This code is released under Apache 2 license
 */
public class EthereumRpc implements EthereumBackend {
    private final Web3JFacade web3JFacade;
    private final EthereumRpcEventGenerator ethereumRpcEventGenerator;

    public EthereumRpc(Web3JFacade web3JFacade, EthereumRpcEventGenerator ethereumRpcEventGenerator) {
        this.web3JFacade = web3JFacade;
        this.ethereumRpcEventGenerator = ethereumRpcEventGenerator;
    }

    @Override
    public GasPrice getGasPrice() {
        return new GasPrice(web3JFacade.getGasPrice());
    }

    @Override
    public EthValue getBalance(EthAddress address) {
        return EthValue.wei(web3JFacade.getBalance(address).getBalance());
    }

    @Override
    public boolean addressExists(EthAddress address) {
        return web3JFacade.getTransactionCount(address).intValue() > 0 || web3JFacade.getBalance(address).getBalance().intValue() > 0 || !web3JFacade.getCode(address).isEmpty();
    }

    @Override
    public EthHash submit(EthAccount account, EthAddress address, EthValue value, EthData data, Nonce nonce, GasUsage gasLimit) {
        RawTransaction tx = createTransaction(account, nonce, getGasPrice(), gasLimit, address, value, data);
        EthData signedMessage = EthData.of(TransactionEncoder.signMessage(tx, Credentials.create(Numeric.toHexStringNoPrefix(account.getBigIntPrivateKey()))));
        web3JFacade.sendTransaction(signedMessage);

        return EthHash.of(Crypto.sha3(signedMessage).data);
    }

    private RawTransaction createTransaction(EthAccount account, Nonce nonce, GasPrice gasPrice, GasUsage gasLimit, EthAddress address, EthValue value, EthData data) {
        return web3JFacade.createTransaction(nonce, gasPrice, gasLimit, address, value, data);
    }

    @Override
    public GasUsage estimateGas(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return new GasUsage(web3JFacade.estimateGas(account, address, value, data));
    }

    @Override
    public Nonce getNonce(EthAddress currentAddress) {
        return new Nonce(web3JFacade.getTransactionCount(currentAddress));
    }

    @Override
    public long getCurrentBlockNumber() {
        return web3JFacade.getCurrentBlockNumber();
    }

    @Override
    public SmartContractByteCode getCode(EthAddress address) {
        return web3JFacade.getCode(address);
    }

    @Override
    public EthData constantCall(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return web3JFacade.constantCall(account, address, data);
    }

    @Override
    public void register(EthereumEventHandler eventHandler) {
        ethereumRpcEventGenerator.addListener(eventHandler);
    }
}
