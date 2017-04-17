package org.adridadou.ethereum.rpc;

import org.adridadou.ethereum.propeller.exception.EthereumApiException;
import org.adridadou.ethereum.propeller.values.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Numeric;
import rx.Observable;

import java.io.IOError;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Created by davidroon on 19.11.16.
 * This code is released under Apache 2 license
 */
public class Web3JFacade {
    public static final BigInteger GAS_LIMIT_FOR_CONSTANT_CALLS = BigInteger.valueOf(1_000_000_000);
    private final Web3j web3j;

    public Web3JFacade(final Web3j web3j) {
        this.web3j = web3j;
    }

    public EthData constantCall(final EthAccount account, final EthAddress address, final EthData data) {
        try {
            return EthData.of(handleError(web3j.ethCall(new Transaction(
                    account.getAddress().withLeading0x(),
                    BigInteger.ZERO,
                    BigInteger.ZERO,
                    GAS_LIMIT_FOR_CONSTANT_CALLS,
                    address.withLeading0x(),
                    BigInteger.ZERO,
                    data.toString()
            ), DefaultBlockParameterName.LATEST).send()));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public BigInteger getTransactionCount(EthAddress address) {
        try {
            return Numeric.decodeQuantity(handleError(web3j.ethGetTransactionCount(address.withLeading0x(), DefaultBlockParameterName.LATEST).send()));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public Observable<EthBlock> observeBlocks() {
        return web3j.blockObservable(true);
    }

    public BigInteger estimateGas(EthAccount account, EthAddress address, EthValue value, EthData data) {
        try {
            return Numeric.decodeQuantity(handleError(web3j.ethEstimateGas(new Transaction(account.getAddress().withLeading0x(), null, null, null,
            		address.isEmpty() ? null : address.withLeading0x(), value.inWei(),  data.toString())).send()));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public BigInteger getGasPrice() {
        try {
            return Numeric.decodeQuantity(handleError(web3j.ethGasPrice().send()));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public EthHash sendTransaction(final EthData rawTransaction) {
        try {
            return EthHash.of(handleError(web3j.ethSendRawTransaction(rawTransaction.withLeading0x()).send()));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public EthGetBalance getBalance(EthAddress address) {
        try {
            return web3j.ethGetBalance(address.withLeading0x(), DefaultBlockParameterName.LATEST).send();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private <S, T extends Response<S>> S handleError(final T response) {
        if (response.hasError()) {
            throw new EthereumApiException(response.getError().getMessage());
        }
        return response.getResult();
    }

    public SmartContractByteCode getCode(EthAddress address) {
        try {
            return SmartContractByteCode.of(web3j.ethGetCode(address.withLeading0x(), DefaultBlockParameterName.LATEST).send().getCode());
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /*
    public <T> Observable<T> event(final EthAddress address,final String eventName, final CallTransaction.Contract contract, Class<T> cls) {
        return web3j.ethLogObservable(new EthFilter(DefaultBlockParameterName.EARLIEST,DefaultBlockParameterName.LATEST,address.withLeading0x()))
                .map(log -> {
                    LogInfo logInfo = new LogInfo(address.address, new ArrayList<>(), EthData.of(log.getData()).data);
                    return contract.parseEvent(logInfo);
                }).filter(invocation -> eventName.equals(invocation.function.name))
                .map(invocation -> outputTypeHandler.convertSpecificType(invocation.args, cls));
    }
    */

    public long getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public RawTransaction createTransaction(Nonce nonce, GasPrice gasPrice, GasUsage gasLimit, EthAddress address, EthValue value, EthData data) {
        return RawTransaction.createTransaction(nonce.getValue(), gasPrice.getPrice(), gasLimit.getUsage(), address.withLeading0x(), value.inWei(), data.withLeading0x());
    }

    public TransactionReceipt getReceipt(EthHash hash) {
        try {
            return handleError(web3j.ethGetTransactionReceipt(hash.withLeading0x()).send());
        } catch (IOException e) {
            throw new EthereumApiException("error while retrieving the transactionReceipt", e);
        }
    }
}
