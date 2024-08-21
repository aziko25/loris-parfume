package loris.parfume.PAYME;

import lombok.RequiredArgsConstructor;
import loris.parfume.Configurations.Telegram.MainTelegramBot;
import loris.parfume.Models.Orders.Orders;
import loris.parfume.PAYME.Exceptions.*;
import loris.parfume.PAYME.Result.*;
import loris.parfume.Repositories.Orders.OrdersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymeMerchantService {

    private static final long TIME_EXPIRED = 43_200_000L;

    private final OrdersRepository ordersRepository;
    private final TransactionRepository transactionRepository;
    private final MainTelegramBot mainTelegramBot;

    @Value("${paymeBusinessId}")
    private String paymeBusinessId;

    @Value("${payment.chat.id}")
    private String paymentChatId;

    private Orders order;

    public Map<String, CheckPerformTransactionResult> checkPerformTransaction(double amount, Long orderId) throws OrderNotExistsException, WrongAmountException {

        order = ordersRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotExistsException("Order not found", -31050, "order"));

        if (amount != order.getTotalSum()) {

            throw new WrongAmountException("Wrong amount", -31001, "amount");
        }

        CheckPerformTransactionResult checkPerformTransactionResult = new CheckPerformTransactionResult();
        checkPerformTransactionResult.setAllow(true);

        Map<String, CheckPerformTransactionResult> result = new HashMap<>();
        result.put("result", checkPerformTransactionResult);

        return result;
    }

    public Map<String, CreateTransactionResult> createTransaction(Long id, Date time, Double amount, Long orderId) throws UnableCompleteException, OrderNotExistsException, WrongAmountException {

        OrderTransaction transaction = transactionRepository.findByPaycomId(orderId);

        if (transaction != null && !Objects.equals(id, transaction.getPaycomId())) {

            throw new UnableCompleteException("Unable to complete operation", -31050, "transaction");
        }

        if (transaction != null && !Objects.equals(transaction.getOrder().getTotalSum(), amount)) {

            throw new UnableCompleteException("Wrong amount", -31001, "transaction");
        }

        transaction = transactionRepository.findByPaycomId(id);

        if (transaction == null) {

            if (checkPerformTransaction(amount, orderId).get("result").isAllow()) {

                OrderTransaction newTransaction = new OrderTransaction();

                newTransaction.setPaycomId(id);
                newTransaction.setPaycomTime(time);
                newTransaction.setCreateTime(new Date());
                newTransaction.setState(TransactionState.STATE_IN_PROGRESS);
                newTransaction.setOrder(order);
                newTransaction.setPerformTime(0L);
                newTransaction.setCancelTime(0L);

                transactionRepository.save(newTransaction);

                CreateTransactionResult createTransactionResult = new CreateTransactionResult(newTransaction.getCreateTime(), newTransaction.getPaycomId().toString(), newTransaction.getState().getCode());
                Map<String, CreateTransactionResult> result = new HashMap<>();
                result.put("result", createTransactionResult);

                return result;
            }
        } else {

            if (transaction.getState() == TransactionState.STATE_IN_PROGRESS) {

                if (System.currentTimeMillis() - transaction.getPaycomTime().getTime() > TIME_EXPIRED) {

                    throw new UnableCompleteException("Transaction is timed out.", -31008, "transaction");

                } else {

                    CreateTransactionResult createTransactionResult = new CreateTransactionResult(transaction.getCreateTime(), transaction.getPaycomId().toString(), transaction.getState().getCode());
                    Map<String, CreateTransactionResult> result = new HashMap<>();
                    result.put("result", createTransactionResult);

                    return result;
                }
            } else {

                throw new UnableCompleteException("Transaction state prevents completion", -31008, "transaction");
            }
        }

        throw new UnableCompleteException("Unable to complete operation", -31008, "transaction");
    }

    public void ifTransactionWasSuccessfullyPerformed() {

        String orderMessage = "Оплата\n-----------\nИмя: " + order.getUser().getFullName() +
                "\nТелефон: " + order.getPhone() +
                "\nАдрес: " + order.getAddress() +
                "\nСсылка на адрес: " + order.getAddressLocationLink() +
                "\nКомментарий: " + order.getComments() +
                "\nФилиал: " + order.getBranch().getName() +
                "\nОбщая Сумма за заказ: " + order.getTotalSum() +
                "\nСумма за доставку: " + order.getSumForDelivery() +
                "\nТовары: " + order.getItemsList().stream()
                .map(ordersItems -> ordersItems.getItem().getNameRu() + " (" + ordersItems.getQuantity() + " шт., размер: " + ordersItems.getSize().getNameRu() + ")")
                .collect(Collectors.joining(", ")) +
                "\nТип Заказа: " + (order.getIsDelivery() ? "Доставка" : "Самовывоз") +
                (order.getIsSoonDeliveryTime() ? "\nДоставка в ближайшее время" : "\nЗапланированное время доставки: " +
                        (order.getScheduledDeliveryTime() != null ? order.getScheduledDeliveryTime().toString() : "Не запланировано"));

        SendMessage message = new SendMessage();

        message.setChatId(paymentChatId);
        message.setText(orderMessage);

        mainTelegramBot.sendMessage(message);
    }

    public Map<String, PerformTransactionResult> performTransaction(Long id) throws TransactionNotFoundException, UnableCompleteException {

        OrderTransaction transaction = transactionRepository.findByPaycomId(id);

        if (transaction != null) {

            if (transaction.getState() == TransactionState.STATE_IN_PROGRESS) {

                if (System.currentTimeMillis() - transaction.getPaycomTime().getTime() > TIME_EXPIRED) {

                    transaction.setState(TransactionState.STATE_CANCELED);
                    transactionRepository.save(transaction);

                    throw new UnableCompleteException("Transaction timed out and was canceled", -31008, "transaction");
                }
                else {

                    transaction.setState(TransactionState.STATE_DONE);
                    transaction.setPerformTimes(new Date());
                    transactionRepository.save(transaction);

                    PerformTransactionResult performTransactionResult = new PerformTransactionResult(transaction.getPaycomId().toString(), transaction.getPerformTime(), transaction.getState().getCode());
                    Map<String, PerformTransactionResult> result = new HashMap<>();
                    result.put("result", performTransactionResult);

                    ifTransactionWasSuccessfullyPerformed();

                    return result;
                }
            }
            else if (transaction.getState() == TransactionState.STATE_DONE) {

                PerformTransactionResult performTransactionResult = new PerformTransactionResult(transaction.getPaycomId().toString(), transaction.getPerformTime(), transaction.getState().getCode());
                Map<String, PerformTransactionResult> result = new HashMap<>();
                result.put("result", performTransactionResult);

                return result;
            }
            else {

                throw new UnableCompleteException("Transaction in an invalid state for completion.", -31008, "transaction");
            }
        }
        else {

            throw new TransactionNotFoundException("Order transaction not found", -31003, "transaction");
        }
    }

    public Map<String, CancelTransactionResult> cancelTransaction(Long id, OrderCancelReason reason) throws UnableCancelTransactionException, TransactionNotFoundException {

        OrderTransaction transaction = transactionRepository.findByPaycomId(id);

        if (transaction != null) {

            switch (transaction.getState()) {

                case STATE_IN_PROGRESS:

                    transaction.setState(TransactionState.STATE_CANCELED);
                    break;

                case STATE_DONE:

                    if (Boolean.TRUE.equals(transaction.getOrder() != null && transaction.getOrder().getIsDelivered())) {

                        throw new UnableCancelTransactionException("Transaction cannot be canceled as the order has been delivered.", -31007, "transaction");
                    }
                    else {

                        transaction.setState(TransactionState.STATE_POST_CANCELED);
                    }
                    break;

                case STATE_POST_CANCELED:

                    break;

                default:
                    transaction.setState(TransactionState.STATE_CANCELED);
                    break;
            }

            if (transaction.getCancelTime() == null || transaction.getCancelTime() == 0) {

                transaction.setCancelTimes(new Date());
            }

            transaction.setReason(reason);
            transactionRepository.save(transaction);

            CancelTransactionResult cancelTransactionResult = new CancelTransactionResult(transaction.getPaycomId().toString(), transaction.getCancelTime(), transaction.getState().getCode());

            Map<String, CancelTransactionResult> result = new HashMap<>();
            result.put("result", cancelTransactionResult);

            return result;
        }
        else {

            throw new TransactionNotFoundException("Order transaction not found", -31003, "transaction");
        }
    }


    public Map<String, CheckTransactionResult> checkTransaction(Long id) throws TransactionNotFoundException {

        OrderTransaction transaction = transactionRepository.findByPaycomId(id);

        if (transaction != null) {

            CheckTransactionResult checkTransactionResult = new CheckTransactionResult(transaction.getCreateTime(),
                    transaction.getPerformTime(),
                    transaction.getCancelTime(),
                    transaction.getPaycomId().toString(),
                    transaction.getState().getCode(),
                    transaction.getReason() != null ? transaction.getReason().getCode() : null);

            Map<String, CheckTransactionResult> result = new HashMap<>();
            result.put("result", checkTransactionResult);

            return result;
        }
        else {

            throw new TransactionNotFoundException("Order transaction not found", -31003, "transaction");
        }
    }

    public Map<String, Object> getStatement(Date from, Date to) {

        List<GetStatementResult> results = new ArrayList<>();

        List<OrderTransaction> transactions = transactionRepository.findByCreateTimeBetween(
                from.getTime(), to.getTime());

        if (transactions != null) {

            results = transactions.stream()
                    .map(transaction -> new GetStatementResult(
                            transaction.getPaycomId().toString(),
                            transaction.getPaycomTime(),
                            transaction.getOrder() != null ? transaction.getOrder().getTotalSum() : null,
                            new Account(transaction.getOrder() != null ? transaction.getOrder().getId().toString() : null),
                            transaction.getCreateTime(),
                            transaction.getPerformTime(),
                            transaction.getCancelTime(),
                            transaction.getId().toString(),
                            transaction.getState().getCode(),
                            transaction.getReason() != null ? transaction.getReason().getCode() : null
                    ))
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("result", new Transactions(results));

        return result;
    }
}