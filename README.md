# RPS Service

* [Overview] (#overview)
* [Target] (#target)
* [Throttling Service Rules] (#throttlingServiceRules)
* [Acceptance criteria] (#acceptanceCriteria)
* [Implementation notes] (#implementationNotes)

## Overview

Реализовать сервис который позволяет ограничивать количество допустимых запросов в секунду 
(ЗВС или request per second RPS) для REST сервиса в соответствии с правилами для конкретных пользователей.

## Target

Сервис который возвращает максимально допустимый ЗВС для конкретного пользователя по его токену. 
У пользователя одновременно может быть несколько используемых токенов. 
В контексте данной задачи этот сервис реализовывать не нужно. 
Достаточно иметь моковую реализацию которая возвращает допустимый ЗВС для пары пользователей 
по паре токенов. Эту же реализацию можно использовать и в тесте.

## Throttling Service Rules

- Если токен не предоставлен тогда пользователь считается неавторизованным.
- Все неавторизованные пользователи лимитированы GuestRPS, 
  (передается параметром при создании ThrottlingServiceImpl, может быть равен для примера 20 
  или любое другое целое число).
- Если токен пришел в запросе но SlaService не вернул еще SLA по пользователю то использовать GuestRPS.
- ЗВС должен расчитываться по пользователю т.к. один и тот же пользователь может иметь разные токены.
- SLA меняется довольно редко, а SlaService довольно долгий при вызове (~200 - 300 миллисекунд на запрос). Поэтому предусмотрите кеширование SLA, и не выполняйте запрос к SlaService если запрос по такому токену уже в процессе.
- Учтите что среднее время отклика REST сервиса должно быть не более 5 миллисекунд. 
  т.е. вызовы ThrottlingService не должны влиять на допустимую скорость работы REST сервиса.

## Acceptance Criteria

- Реализовать class ThrottlingServiceImpl implements ThrottlingService
- Покрыть реализацию тестами которые доказывают правильность его работы.


Дополнительно (по желанию и наличию времени):
- реализовать любой REST сервис использующий данную реализацию ThrottlingService.
- написать тест на нагрузочное тестирование данного REST сервиса
- сравнить скорость работы REST сервиса с использованием ThrottlingService и без.


Для нас важна именно ваша реализация этого задания. т.е. не разрешается копирование 
решения из интернета или реализация кем либо еще кроме вас. 
Можно использовать любые вспомогательные библиотеки, кроме собственно являющихся 
реализацией подобного сервиса. Лучше написать решение с минимальным функционалом но работающее, 
чем включающее все требования но не работающее.

## Implementation notes
* Task description contains unaccurate requirement:
  Все неавторизованные пользователи лимитированы GuestRPS,
  (передается параметром при создании ThrottlingServiceImpl, может быть равен для примера 20
  или любое другое целое число).
  How I understand it:
  1. if method signature contains only TokenId -> we cannot distinguish different users. 
  So, we restrict GuestRPS(by default == 20) for all users which could not provide token.
     So, if GuestRPS==20, and 21 users try to access to our ThrottlingService, only first 20 can do it.
  2. If we can change method signature and add to isRequestAllowed String userId param - we can support
     different users.
* Our solution should provide RPS which allows users to interact with third-party REST service.
  Our service should have response time ~ 5ms. Sla service ~ 200-300 ms. So, we should return RPS in 5 ms
  even if SlaCache doesn't have value for this user. Of course,
  during waiting SlaService, user can access to us multiple times,
  so we have to support our RPS while SlaService respond to us with available RPS and compare 2 values
  and sync them.